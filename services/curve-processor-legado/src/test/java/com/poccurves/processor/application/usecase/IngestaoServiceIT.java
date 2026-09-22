package com.poccurves.processor.application.usecase;
import com.poccurves.processor.application.model.B3CurvaProntaParser;
import com.poccurves.processor.application.model.EstadoLoteIngestao;
import com.poccurves.processor.application.model.ParseResult;
import com.poccurves.processor.application.model.PontoDadoMercado;
import com.poccurves.processor.application.model.ResultadoProcessamentoBloco;
import com.poccurves.processor.application.model.TipoPayload;

import com.poccurves.processor.CurveProcessorApplication;
import com.poccurves.processor.testsupport.CurvaProntaTestFixtures;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Teste de integração real: exige o ambiente local Podman de pé
 * (`deploy/podman/up.sh --lite`), com o SQL Server já migrado até V8 e
 * acessível em localhost:1433. Nomeado com sufixo "IT" (não "Test") de
 * propósito — o Surefire (`mvn test`) só coleta `**&#47;*Test.java`, então
 * este teste fica de fora do build padrão e só roda quando chamado
 * explicitamente (`-Dtest=IngestaoServiceIT`), exatamente como o teste de
 * contrato contra fonte real do feeder (tarefa 6.8 do feeder).
 * <p>
 * Prova, contra o banco de dados de verdade e a credencial restrita real
 * (curve_processor_app — tarefa 1.6), o caminho completo: parse de um bloco
 * real de curva pronta B3 (B3_CURVA_PRE) -&gt; persistência transacional -&gt;
 * consolidação do lote -&gt; detecção de divergência -&gt; idempotência de
 * redelivery.
 */
@SpringBootTest(classes = CurveProcessorApplication.class)
class IngestaoServiceIT {

    @DynamicPropertySource
    static void datasourceLocal(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",
                () -> "jdbc:sqlserver://localhost:1433;databaseName=curvasdb;trustServerCertificate=true;encrypt=true");
        // Fora da rede do compose (teste roda no host), o listener externo do Kafka é outra porta — ver compose.core.yaml.
        registry.add("spring.kafka.bootstrap-servers", () -> "localhost:19092");
    }

    @Autowired
    private IngestaoService ingestaoService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * curve_processor_app (o usuário real da aplicação) não tem permissão de
     * DELETE — de propósito, verificado no primeiro run deste teste (a
     * fronteira de escrita da tarefa 1.6 é SELECT/INSERT/UPDATE, nunca
     * DELETE). Setup/teardown do teste usa SA só para isto.
     */
    private static JdbcTemplate jdbcTemplateSa() {
        DriverManagerDataSource ds = new DriverManagerDataSource(
                "jdbc:sqlserver://localhost:1433;databaseName=curvasdb;trustServerCertificate=true;encrypt=true",
                "sa", "CurvasP0c!Local");
        ds.setDriverClassName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
        return new JdbcTemplate(ds);
    }

    private void limparDadosDeTeste() {
        JdbcTemplate sa = jdbcTemplateSa();
        sa.update("DELETE FROM ponto_dado_mercado WHERE conjunto_dados = 'B3_CURVA_PRE' AND chave_instrumento IN ('10','21','32') AND data_referencia = '2026-08-21'");
        sa.update("DELETE FROM lote_ingestao WHERE lote_externo_id LIKE 'it-lote-%'");
    }

    @AfterEach
    void depois() {
        limparDadosDeTeste();
    }

    /** 3 linhas reais de curva pronta B3 (Descrição;Dias Úteis;Dias Corridos;Preço/Taxa) — mesmo formato de {@code B3CurvaProntaParserTest}. */
    private byte[] conteudoCurvaPronta() {
        String[] linhas = {
                CurvaProntaTestFixtures.linha("10", "14", "14,129"),
                CurvaProntaTestFixtures.linha("21", "30", "14,334"),
                CurvaProntaTestFixtures.linha("32", "45", "14,448")
        };
        return String.join("\n", linhas).getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void processaBlocoRealDaB3PersisteConsolidaEDetectaDivergenciaEmRedelivery() {
        String loteExternoId = "it-lote-" + UUID.randomUUID();
        limparDadosDeTeste();

        B3CurvaProntaParser parser = new B3CurvaProntaParser("B3_CURVA_PRE");
        ParseResult resultado = parser.parse(conteudoCurvaPronta(), StandardCharsets.UTF_8.name(), LocalDate.of(2026, 8, 21));
        List<PontoDadoMercado> pontos = ((ParseResult.Sucesso) resultado).pontos();
        assertThat(pontos).hasSize(3);

        // 1) Primeiro bloco (único, totalBlocos=1) processado -> lote deve consolidar direto para COMPLETO
        ResultadoProcessamentoBloco r1 = ingestaoService.processarBloco(
                "B3", "B3_CURVA_PRE", LocalDate.of(2026, 8, 21), loteExternoId,
                UUID.randomUUID(), "evt-1", "sha256:" + "a".repeat(64), 1, 1, TipoPayload.INDIVIDUAL_QUOTES, pontos);

        assertThat(r1.lote().estado()).isEqualTo(EstadoLoteIngestao.COMPLETO);
        assertThat(r1.lote().pontosGravados()).isEqualTo(3);
        assertThat(r1.divergenciasNoBloco()).isEmpty();

        BigDecimal valorGravado = jdbcTemplate.queryForObject(
                "SELECT valor FROM ponto_dado_mercado WHERE fonte='B3' AND conjunto_dados='B3_CURVA_PRE' AND data_referencia='2026-08-21' AND chave_instrumento='10'",
                BigDecimal.class);
        assertThat(valorGravado).isEqualByComparingTo("14.129");

        // 2) Redelivery do MESMO eventId -> no-op idempotente, nada muda
        ResultadoProcessamentoBloco r2 = ingestaoService.processarBloco(
                "B3", "B3_CURVA_PRE", LocalDate.of(2026, 8, 21), loteExternoId,
                UUID.randomUUID(), "evt-1", "sha256:" + "a".repeat(64), 1, 1, TipoPayload.INDIVIDUAL_QUOTES, pontos);
        assertThat(r2.divergenciasNoBloco()).isEmpty();
        assertThat(r2.lote().blocosRecebidos()).isEqualTo(1);

        // 3) Um lote NOVO (loteExternoId diferente) regravando a chave '10' com valor diferente -> divergência detectada
        String segundoLoteExternoId = "it-lote-" + UUID.randomUUID();
        PontoDadoMercado pontoDivergente = new PontoDadoMercado(
                "B3", "B3_CURVA_PRE", LocalDate.of(2026, 8, 21), "10",
                new BigDecimal("99.999"), B3CurvaProntaParser.TIPO_COTACAO_TAXA_CURVA_PRONTA, null);

        ResultadoProcessamentoBloco r3 = ingestaoService.processarBloco(
                "B3", "B3_CURVA_PRE", LocalDate.of(2026, 8, 21), segundoLoteExternoId,
                UUID.randomUUID(), "evt-1", "sha256:" + "b".repeat(64), 1, 1, TipoPayload.INDIVIDUAL_QUOTES, List.of(pontoDivergente));

        assertThat(r3.divergenciasNoBloco()).hasSize(1);
        assertThat(r3.divergenciasNoBloco().get(0).valorAnterior()).isEqualByComparingTo("14.129");
        assertThat(r3.divergenciasNoBloco().get(0).valorNovo()).isEqualByComparingTo("99.999");
        assertThat(r3.lote().pontosDivergentes()).isEqualTo(1);
    }
}
