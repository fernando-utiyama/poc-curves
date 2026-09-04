package com.poccurves.processor.application;
import com.poccurves.processor.domain.ingestao.EstadoLoteIngestao;
import com.poccurves.processor.domain.ingestao.PontoDadoMercado;
import com.poccurves.processor.domain.ingestao.ResultadoProcessamentoBloco;
import com.poccurves.processor.domain.parsing.Bvbg086PricRptParser;
import com.poccurves.processor.domain.parsing.ParseResult;
import com.poccurves.processor.domain.parsing.TipoPayload;

import com.poccurves.processor.CurveProcessorApplication;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.io.InputStream;
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
 * (curve_processor_app — tarefa 1.6), o caminho completo: parse do fixture
 * real da B3 -&gt; persistência transacional -&gt; consolidação do lote -&gt;
 * detecção de divergência -&gt; idempotência de redelivery.
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
        sa.update("DELETE FROM ponto_dado_mercado WHERE conjunto_dados = 'BVBG.086' AND chave_instrumento IN ('DI1Z28','DI1J30','DI1V31') AND data_referencia = '2026-08-21'");
        sa.update("DELETE FROM lote_ingestao WHERE lote_externo_id LIKE 'it-lote-%'");
    }

    @AfterEach
    void depois() {
        limparDadosDeTeste();
    }

    private byte[] lerFixture() throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/fixtures/bvbg086_di1_4blocos.xml")) {
            assertThat(in).isNotNull();
            return in.readAllBytes();
        }
    }

    @Test
    void processaBlocoRealDaB3PersisteConsolidaEDetectaDivergenciaEmRedelivery() throws IOException {
        String loteExternoId = "it-lote-" + UUID.randomUUID();
        limparDadosDeTeste();

        Bvbg086PricRptParser parser = new Bvbg086PricRptParser("BVBG.086");
        ParseResult resultado = parser.parse(lerFixture(), StandardCharsets.UTF_8.name(), LocalDate.of(2026, 8, 21));
        List<PontoDadoMercado> pontos = ((ParseResult.Sucesso) resultado).pontos();
        assertThat(pontos).hasSize(3);

        // 1) Primeiro bloco (único, totalBlocos=1) processado -> lote deve consolidar direto para COMPLETO
        ResultadoProcessamentoBloco r1 = ingestaoService.processarBloco(
                "B3", "BVBG.086", LocalDate.of(2026, 8, 21), loteExternoId,
                UUID.randomUUID(), "evt-1", "sha256:" + "a".repeat(64), 1, 1, TipoPayload.INDIVIDUAL_QUOTES, pontos);

        assertThat(r1.lote().estado()).isEqualTo(EstadoLoteIngestao.COMPLETO);
        assertThat(r1.lote().pontosGravados()).isEqualTo(3);
        assertThat(r1.divergenciasNoBloco()).isEmpty();

        BigDecimal valorGravado = jdbcTemplate.queryForObject(
                "SELECT valor FROM ponto_dado_mercado WHERE fonte='B3' AND conjunto_dados='BVBG.086' AND data_referencia='2026-08-21' AND chave_instrumento='DI1Z28'",
                BigDecimal.class);
        assertThat(valorGravado).isEqualByComparingTo("14.129");

        // 2) Redelivery do MESMO eventId -> no-op idempotente, nada muda
        ResultadoProcessamentoBloco r2 = ingestaoService.processarBloco(
                "B3", "BVBG.086", LocalDate.of(2026, 8, 21), loteExternoId,
                UUID.randomUUID(), "evt-1", "sha256:" + "a".repeat(64), 1, 1, TipoPayload.INDIVIDUAL_QUOTES, pontos);
        assertThat(r2.divergenciasNoBloco()).isEmpty();
        assertThat(r2.lote().blocosRecebidos()).isEqualTo(1);

        // 3) Um lote NOVO (loteExternoId diferente) regravando DI1Z28 com valor diferente -> divergência detectada
        String segundoLoteExternoId = "it-lote-" + UUID.randomUUID();
        PontoDadoMercado pontoDivergente = new PontoDadoMercado(
                "B3", "BVBG.086", LocalDate.of(2026, 8, 21), "DI1Z28",
                new BigDecimal("99.999"), Bvbg086PricRptParser.TIPO_COTACAO_TAXA_AJUSTE, null);

        ResultadoProcessamentoBloco r3 = ingestaoService.processarBloco(
                "B3", "BVBG.086", LocalDate.of(2026, 8, 21), segundoLoteExternoId,
                UUID.randomUUID(), "evt-1", "sha256:" + "b".repeat(64), 1, 1, TipoPayload.INDIVIDUAL_QUOTES, List.of(pontoDivergente));

        assertThat(r3.divergenciasNoBloco()).hasSize(1);
        assertThat(r3.divergenciasNoBloco().get(0).valorAnterior()).isEqualByComparingTo("14.129");
        assertThat(r3.divergenciasNoBloco().get(0).valorNovo()).isEqualByComparingTo("99.999");
        assertThat(r3.lote().pontosDivergentes()).isEqualTo(1);
    }
}
