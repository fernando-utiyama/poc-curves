package com.poccurves.processor.adapter.in.messaging;

import com.poccurves.processor.CurveProcessorApplication;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Teste de integracao real: exige o ambiente local Podman de pe
 * (`deploy/podman/up.sh --lite`), com o SQL Server e o Kafka reais
 * acessiveis (localhost:1433 e localhost:19092).
 * <p>
 * Publica uma mensagem READY_CURVE real (formato de curva pronta B3,
 * "Descricao;DiasUteis;DiasCorridos;Taxa") no topico marketdata.rotina.v1 e
 * comprova, contra o banco real, que o caminho ponta a ponta
 * (IngestaoListener -&gt; ProcessarEnvelopeIngestaoUseCase -&gt; IngestaoService
 * -&gt; PublicacaoCurvaService) publica uma versao_curva IMPORTADA com os
 * vertices corretos.
 * <p>
 * <b>Nota de proveniencia</b>: este arquivo foi reconstruido a partir do
 * bytecode compilado (target/test-classes) depois de ter sido apagado por
 * engano nesta sessao (rm -rf de um diretorio antigo antes de copiar este
 * arquivo especifico para o pacote novo) durante a migracao para arquitetura
 * hexagonal — SQL, JSON do envelope e asserções foram recuperados
 * literalmente da constant pool da classe compilada; nomes de variavel local
 * e comentarios que so existiam no fonte original podem diferir do arquivo
 * anterior.
 */
@SpringBootTest(classes = CurveProcessorApplication.class)
class IngestaoListenerReadyCurvePublicacaoIT {

    private static final String CODIGO_IMPORTADA = "B3_CURVA_PRE";

    @Autowired
    private IngestaoListener listener;

    private UUID definicaoId;
    private UUID execucaoId;
    private UUID correlationId;

    @DynamicPropertySource
    static void datasourceLocal(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",
                () -> "jdbc:sqlserver://localhost:1433;databaseName=curvasdb;trustServerCertificate=true;encrypt=true");
        registry.add("spring.kafka.bootstrap-servers", () -> "localhost:19092");
    }

    private static JdbcTemplate jdbcTemplateSa() {
        DriverManagerDataSource ds = new DriverManagerDataSource(
                "jdbc:sqlserver://localhost:1433;databaseName=curvasdb;trustServerCertificate=true;encrypt=true",
                "sa", "CurvasP0c!Local");
        ds.setDriverClassName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
        return new JdbcTemplate(ds);
    }

    private void semearDefinicaoImportada(JdbcTemplate sa) {
        definicaoId = UUID.randomUUID();
        sa.update(
                "INSERT INTO definicao_curva (id, codigo, nome, moeda, modo_origem, horario_limite_publicacao, estado, criado_por) " +
                        "VALUES (?, ?, ?, 'BRL', 'IMPORTED', '18:00', 'ATIVA', 'it-listener')",
                definicaoId.toString(), CODIGO_IMPORTADA, CODIGO_IMPORTADA);

        sa.update(
                "INSERT INTO versao_definicao_curva (id, definicao_curva_id, numero_versao, contagem_dias, calendario, " +
                        "interpolador, politica_extrapolacao, politica_arredondamento, vigencia_inicio, vigencia_fim) " +
                        "VALUES (?, ?, 1, 'DU/252', 'ANBIMA', 'LINEAR', 'TAXA_CONSTANTE', 'PADRAO', '2020-01-01', NULL)",
                UUID.randomUUID().toString(), definicaoId.toString());
    }

    private void semearExecucaoCurva(JdbcTemplate sa) {
        execucaoId = UUID.randomUUID();
        correlationId = UUID.randomUUID();
        sa.update(
                "INSERT INTO execucao_curva (id, correlacao_id, disparo, faixa, estado, momento_curva) " +
                        "VALUES (?, ?, 'MANUAL', 'PRIORITARIA', 'CONCLUIDA', 'FECHAMENTO')",
                execucaoId.toString(), correlationId.toString());
    }

    private void limparResiduoPontosELotes() {
        JdbcTemplate sa = jdbcTemplateSa();
        sa.update("DELETE FROM ponto_dado_mercado WHERE conjunto_dados = ?", CODIGO_IMPORTADA);
        sa.update("DELETE FROM lote_ingestao WHERE conjunto_dados = ?", CODIGO_IMPORTADA);
    }

    @BeforeEach
    void prepararIsolado() {
        limparResiduoPontosELotes();
    }

    @AfterEach
    void limpar() {
        JdbcTemplate sa = jdbcTemplateSa();
        sa.update("DELETE FROM validacao_curva WHERE versao_curva_id IN (SELECT id FROM versao_curva WHERE definicao_curva_id = ?)", definicaoId.toString());
        sa.update("DELETE FROM procedencia_curva WHERE versao_curva_id IN (SELECT id FROM versao_curva WHERE definicao_curva_id = ?)", definicaoId.toString());
        sa.update("DELETE FROM vertice_curva WHERE versao_curva_id IN (SELECT id FROM versao_curva WHERE definicao_curva_id = ?)", definicaoId.toString());
        sa.update("DELETE FROM versao_curva WHERE definicao_curva_id = ?", definicaoId.toString());
        sa.update("DELETE FROM versao_definicao_curva WHERE definicao_curva_id = ?", definicaoId.toString());
        sa.update("DELETE FROM definicao_curva WHERE id = ?", definicaoId.toString());
        sa.update("DELETE FROM execucao_curva WHERE id = ?", execucaoId.toString());
    }

    private String envelopeReadyCurveJson(String loteId) {
        String records = String.join(",",
                "{\"raw\":\"DI x pré;1;3;13,90\"}",
                "{\"raw\":\"DI x pré;4;6;13,90\"}",
                "{\"raw\":\"DI x pré;10;14;13,87\"}");

        return """
                {
                  "eventId": "%s",
                  "correlationId": "%s",
                  "source": "B3",
                  "dataset": "%s",
                  "referenceDate": "2026-08-21",
                  "producedAt": "2026-08-21T18:00:00Z",
                  "schemaVersion": "1.0",
                  "payloadKind": "READY_CURVE",
                  "loteId": "%s",
                  "sequencia": 1,
                  "totalBlocos": 1,
                  "payload": {
                    "sourceUrl": "https://sistemaswebb3-derivativos.b3.com.br/referenceRatesProxy/teste",
                    "encoding": "ISO-8859-1",
                    "contentHash": "sha256:%s",
                    "sizeBytes": 60,
                    "records": [%s]
                  }
                }
                """.formatted(UUID.randomUUID(), correlationId, CODIGO_IMPORTADA, loteId, "b".repeat(64), records);
    }

    @Test
    void mensagemReadyCurveRealPublicaVersaoImportadaComVerticesCorretos() {
        JdbcTemplate sa = jdbcTemplateSa();
        semearDefinicaoImportada(sa);
        semearExecucaoCurva(sa);

        String loteId = "it-readycurve-" + UUID.randomUUID();
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                "marketdata.rotina.v1", 0, 0L, "chave", envelopeReadyCurveJson(loteId));
        Acknowledgment ack = mock(Acknowledgment.class);

        listener.ouvirRotina(record, ack);
        verify(ack).acknowledge();

        List<Map<String, Object>> versoes = sa.queryForList(
                "SELECT id, origem_versao, numero_versao FROM versao_curva WHERE definicao_curva_id = ?",
                definicaoId.toString());
        assertThat(versoes).hasSize(1);
        assertThat(versoes.get(0).get("origem_versao")).isEqualTo("IMPORTADA");

        String versaoCurvaId = versoes.get(0).get("id").toString();
        List<Map<String, Object>> vertices = sa.queryForList(
                "SELECT prazo_dias_uteis, taxa FROM vertice_curva WHERE versao_curva_id = ? ORDER BY prazo_dias_uteis",
                versaoCurvaId);
        assertThat(vertices).hasSize(3);
        assertThat(((Number) vertices.get(0).get("prazo_dias_uteis")).intValue()).isEqualTo(1);
        assertThat((BigDecimal) vertices.get(0).get("taxa")).isEqualByComparingTo("13.90");
        assertThat(((Number) vertices.get(2).get("prazo_dias_uteis")).intValue()).isEqualTo(10);
        assertThat((BigDecimal) vertices.get(2).get("taxa")).isEqualByComparingTo("13.87");

        Integer pontosGravados = sa.queryForObject(
                "SELECT COUNT(*) FROM ponto_dado_mercado WHERE conjunto_dados = ?", Integer.class, CODIGO_IMPORTADA);
        assertThat(pontosGravados).isEqualTo(3);

        String execucaoVinculada = sa.queryForObject(
                "SELECT execucao_curva_id FROM lote_ingestao WHERE lote_externo_id = ?", String.class, loteId);
        assertThat(execucaoVinculada).isEqualToIgnoringCase(execucaoId.toString());
    }
}
