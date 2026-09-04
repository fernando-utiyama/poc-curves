package com.poccurves.processor.adapter.in.messaging;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.List;
import java.util.UUID;

import com.poccurves.processor.CurveProcessorApplication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Teste de integracao real: exige o ambiente local Podman de pe
 * (`deploy/podman/up.sh --lite`), com o SQL Server real acessivel em
 * localhost:1433.
 * <p>
 * Cobre a tarefa 8.2 do backlog: reprocessar ("dar replay") em todas as
 * mensagens de um lote ja completo tem que terminar com o mesmo estado final
 * — sem duplicar pontos nem contadores. Diferente da tarefa 8.1 (que testa
 * apenas a redelivery da MESMA ultima mensagem, via o eventId curto-circuito
 * no topo de {@code IngestaoService.processarBloco}), este teste reenvia
 * TODOS os blocos do lote, na mesma ordem, depois que o lote ja esta
 * COMPLETO — exercitando o outro caminho de protecao (o guarda de estado
 * != ABERTO), que e o que de fato protege contra um replay integral do
 * topico (ex. apos reset de offset do consumer group).
 * <p>
 * Chama {@link IngestaoListener#ouvirRotina} diretamente com
 * {@link ConsumerRecord} fabricados — mesma tecnica de
 * {@code IngestaoListenerTest} — mas aqui com o {@link IngestaoListener} real
 * (nao mockado), incluindo {@code IngestaoService} e banco reais, para provar
 * a propriedade de ponta a ponta.
 */
@SpringBootTest(classes = CurveProcessorApplication.class)
class ReplayTopicoIT {

    @DynamicPropertySource
    static void datasourceLocal(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",
                () -> "jdbc:sqlserver://localhost:1433;databaseName=curvasdb;trustServerCertificate=true;encrypt=true");
        registry.add("spring.kafka.bootstrap-servers", () -> "localhost:19092");
    }

    @Autowired
    private IngestaoListener ingestaoListener;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static JdbcTemplate jdbcTemplateSa() {
        DriverManagerDataSource ds = new DriverManagerDataSource(
                "jdbc:sqlserver://localhost:1433;databaseName=curvasdb;trustServerCertificate=true;encrypt=true",
                "sa", "CurvasP0c!Local");
        ds.setDriverClassName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
        return new JdbcTemplate(ds);
    }

    private void limparDadosDeTeste() {
        JdbcTemplate sa = jdbcTemplateSa();
        sa.update("DELETE FROM ponto_dado_mercado WHERE chave_instrumento LIKE 'REPLAY_%'");
        sa.update("DELETE FROM lote_ingestao WHERE lote_externo_id LIKE 'it-replay-%'");
    }

    @AfterEach
    void depois() {
        limparDadosDeTeste();
    }

    private String envelope(String loteId, String eventId, int sequencia, String ticker) {
        String fragmento = "<BizGrp><Document><PricRpt><SctyId><TckrSymb>" + ticker + "</TckrSymb></SctyId>"
                + "<FinInstrmId><OthrId><Id>x</Id></OthrId></FinInstrmId>"
                + "<TradDt><Dt>2026-08-21</Dt></TradDt>"
                + "<FinInstrmAttrbts><AdjstdQtTax>10.000</AdjstdQtTax></FinInstrmAttrbts>"
                + "</PricRpt></Document></BizGrp>";
        return """
                {
                  "eventId": "%s",
                  "correlationId": "%s",
                  "source": "B3",
                  "dataset": "BVBG.086",
                  "referenceDate": "2026-08-21",
                  "producedAt": "2026-08-21T18:00:00Z",
                  "schemaVersion": "1.0",
                  "payloadKind": "INDIVIDUAL_QUOTES",
                  "loteId": "%s",
                  "sequencia": %d,
                  "totalBlocos": 3,
                  "payload": {
                    "sourceUrl": "file://teste",
                    "encoding": "UTF-8",
                    "contentHash": "sha256:%s",
                    "sizeBytes": 10,
                    "records": [{"raw": "%s"}]
                  }
                }
                """.formatted(eventId, UUID.randomUUID(), loteId, sequencia, "a".repeat(64), fragmento);
    }

    @Test
    void replayDeTodosOsBlocosDeUmLoteJaCompletoMantemEstadoFinalIdentico() {
        String loteId = "it-replay-" + UUID.randomUUID();

        String eventId1 = UUID.randomUUID().toString();
        String eventId2 = UUID.randomUUID().toString();
        String eventId3 = UUID.randomUUID().toString();

        List<ConsumerRecord<String, String>> blocos = List.of(
                new ConsumerRecord<>("marketdata.rotina.v1", 0, 0L, loteId,
                        envelope(loteId, eventId1, 1, "REPLAY_A")),
                new ConsumerRecord<>("marketdata.rotina.v1", 0, 1L, loteId,
                        envelope(loteId, eventId2, 2, "REPLAY_B")),
                new ConsumerRecord<>("marketdata.rotina.v1", 0, 2L, loteId,
                        envelope(loteId, eventId3, 3, "REPLAY_C")));

        // Passe 1: consumo original do topico inteiro, em ordem.
        for (ConsumerRecord<String, String> bloco : blocos) {
            ingestaoListener.ouvirRotina(bloco, mock(Acknowledgment.class));
        }

        String estadoAntes = jdbcTemplate.queryForObject(
                "SELECT estado FROM lote_ingestao WHERE lote_externo_id = ?", String.class, loteId);
        Integer blocosAntes = jdbcTemplate.queryForObject(
                "SELECT blocos_recebidos FROM lote_ingestao WHERE lote_externo_id = ?", Integer.class, loteId);
        Integer pontosGravadosAntes = jdbcTemplate.queryForObject(
                "SELECT pontos_gravados FROM lote_ingestao WHERE lote_externo_id = ?", Integer.class, loteId);
        Integer contagemLotesAntes = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM lote_ingestao WHERE lote_externo_id = ?", Integer.class, loteId);
        Integer contagemPontosAntes = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ponto_dado_mercado WHERE chave_instrumento IN ('REPLAY_A','REPLAY_B','REPLAY_C')",
                Integer.class);

        assertThat(estadoAntes).isEqualTo("COMPLETO");
        assertThat(blocosAntes).isEqualTo(3);
        assertThat(pontosGravadosAntes).isEqualTo(3);
        assertThat(contagemLotesAntes).isEqualTo(1);
        assertThat(contagemPontosAntes).isEqualTo(3);

        // Passe 2: replay do topico inteiro — os TRES blocos reenviados na mesma
        // ordem, simulando um reset de offset do consumer group para o inicio.
        for (ConsumerRecord<String, String> bloco : blocos) {
            ingestaoListener.ouvirRotina(bloco, mock(Acknowledgment.class));
        }

        String estadoDepois = jdbcTemplate.queryForObject(
                "SELECT estado FROM lote_ingestao WHERE lote_externo_id = ?", String.class, loteId);
        Integer blocosDepois = jdbcTemplate.queryForObject(
                "SELECT blocos_recebidos FROM lote_ingestao WHERE lote_externo_id = ?", Integer.class, loteId);
        Integer pontosGravadosDepois = jdbcTemplate.queryForObject(
                "SELECT pontos_gravados FROM lote_ingestao WHERE lote_externo_id = ?", Integer.class, loteId);
        Integer contagemLotesDepois = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM lote_ingestao WHERE lote_externo_id = ?", Integer.class, loteId);
        Integer contagemPontosDepois = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ponto_dado_mercado WHERE chave_instrumento IN ('REPLAY_A','REPLAY_B','REPLAY_C')",
                Integer.class);

        assertThat(estadoDepois).isEqualTo(estadoAntes);
        assertThat(blocosDepois).isEqualTo(blocosAntes);
        assertThat(pontosGravadosDepois).isEqualTo(pontosGravadosAntes);
        assertThat(contagemLotesDepois)
                .as("replay do topico inteiro nao pode ter criado uma segunda linha de lote_ingestao")
                .isEqualTo(contagemLotesAntes);
        assertThat(contagemPontosDepois)
                .as("replay do topico inteiro nao pode ter duplicado nenhum ponto de mercado")
                .isEqualTo(contagemPontosAntes);
    }
}
