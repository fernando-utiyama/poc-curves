package com.poccurves.processor.adapter.in.messaging;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import com.poccurves.processor.CurveProcessorApplication;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Teste de integracao real: exige o ambiente local Podman de pe
 * (`deploy/podman/up.sh --lite`), com o SQL Server e o Kafka reais
 * acessiveis (localhost:1433 e localhost:19092).
 * <p>
 * Cobre a tarefa 8.18 do backlog: a faixa de rotina ocupada (processando um
 * lote grande, com muitas escritas sequenciais no banco) nao pode impedir o
 * consumo da faixa prioritaria — cada faixa e um topico Kafka distinto com
 * seu proprio consumer group e sua propria thread de container
 * ({@code KafkaConsumerConfig}), entao a garantia de isolamento e uma
 * propriedade da configuracao do container, nao da logica de negocio. Este
 * teste publica de verdade num topico e no outro e observa o resultado no
 * banco de dados real, em vez de inspecionar a configuracao.
 * <p>
 * Para criar uma janela de tempo real (sem tocar em codigo de producao para
 * simular lentidao artificial), o lote de rotina traz muitos pontos de
 * mercado (1500), forcando 1500 pares de ida-e-volta sequenciais ao SQL
 * Server dentro da mesma transacao ({@code IngestaoService.processarBloco}
 * grava um por um). O lote da prioritaria tem um unico ponto e deve
 * completar bem antes do lote de rotina.
 */
@SpringBootTest(classes = CurveProcessorApplication.class)
class IsolamentoFaixasIT {

    @DynamicPropertySource
    static void datasourceLocal(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",
                () -> "jdbc:sqlserver://localhost:1433;databaseName=curvasdb;trustServerCertificate=true;encrypt=true");
        registry.add("spring.kafka.bootstrap-servers", () -> "localhost:19092");
    }

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

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
        sa.update("DELETE FROM ponto_dado_mercado WHERE chave_instrumento LIKE 'LANE_%'");
        sa.update("DELETE FROM lote_ingestao WHERE lote_externo_id LIKE 'it-lane-%'");
    }

    @AfterEach
    void depois() {
        limparDadosDeTeste();
    }

    private String fragmentoBizGrp(String ticker) {
        return "<BizGrp><Document><PricRpt><SctyId><TckrSymb>" + ticker + "</TckrSymb></SctyId>"
                + "<FinInstrmId><OthrId><Id>x</Id></OthrId></FinInstrmId>"
                + "<TradDt><Dt>2026-08-21</Dt></TradDt>"
                + "<FinInstrmAttrbts><AdjstdQtTax>10.000</AdjstdQtTax></FinInstrmAttrbts>"
                + "</PricRpt></Document></BizGrp>";
    }

    private String envelope(String loteId, List<String> tickers) {
        StringBuilder records = new StringBuilder("[");
        for (int i = 0; i < tickers.size(); i++) {
            if (i > 0) {
                records.append(",");
            }
            records.append("{\"raw\":\"").append(fragmentoBizGrp(tickers.get(i))).append("\"}");
        }
        records.append("]");

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
                  "sequencia": 1,
                  "totalBlocos": 1,
                  "payload": {
                    "sourceUrl": "file://teste",
                    "encoding": "UTF-8",
                    "contentHash": "sha256:%s",
                    "sizeBytes": 10,
                    "records": %s
                  }
                }
                """.formatted(UUID.randomUUID(), UUID.randomUUID(), loteId, "a".repeat(64), records);
    }

    private void aguardarAte(BooleanSupplier condicao, Duration tempoMaximo) throws InterruptedException {
        Instant limite = Instant.now().plus(tempoMaximo);
        while (Instant.now().isBefore(limite)) {
            if (condicao.getAsBoolean()) {
                return;
            }
            Thread.sleep(150);
        }
        assertThat(condicao.getAsBoolean())
                .as("condicao nao satisfeita dentro de " + tempoMaximo)
                .isTrue();
    }

    private boolean existePonto(String chaveInstrumento) {
        Integer contagem = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ponto_dado_mercado WHERE chave_instrumento = ?",
                Integer.class, chaveInstrumento);
        return contagem != null && contagem > 0;
    }

    private boolean loteCompleto(String loteExternoId) {
        List<String> estados = jdbcTemplate.query(
                "SELECT estado FROM lote_ingestao WHERE lote_externo_id = ?",
                (rs, rowNum) -> rs.getString("estado"), loteExternoId);
        return !estados.isEmpty() && "COMPLETO".equals(estados.get(0));
    }

    @Test
    void faixaRotinaOcupadaNaoImpedeConsumoDaPrioritaria() throws Exception {
        String loteRotina = "it-lane-rotina-" + UUID.randomUUID();
        String lotePrioritaria = "it-lane-prioritaria-" + UUID.randomUUID();

        List<String> tickersRotina = new ArrayList<>();
        for (int i = 0; i < 1500; i++) {
            tickersRotina.add("LANE_ROTINA_" + String.format("%05d", i));
        }
        String envelopeRotina = envelope(loteRotina, tickersRotina);
        String envelopePrioritaria = envelope(lotePrioritaria, List.of("LANE_PRIORITARIA_MARK"));

        long inicio = System.currentTimeMillis();
        kafkaTemplate.send("marketdata.rotina.v1", loteRotina, envelopeRotina).get(10, TimeUnit.SECONDS);
        kafkaTemplate.send("marketdata.prioritaria.v1", lotePrioritaria, envelopePrioritaria).get(10, TimeUnit.SECONDS);

        // A faixa prioritaria tem seu proprio consumer group/thread — nao pode
        // ficar presa atras do lote grande da rotina, que ainda esta sendo
        // gravado ponto a ponto no banco.
        aguardarAte(() -> existePonto("LANE_PRIORITARIA_MARK"), Duration.ofSeconds(10));
        long duracaoPrioritaria = System.currentTimeMillis() - inicio;

        // A faixa de rotina eventualmente completa tambem — o lote grande nao trava,
        // so demora mais por ter 1500 pontos gravados sequencialmente.
        aguardarAte(() -> loteCompleto(loteRotina), Duration.ofSeconds(90));
        long duracaoRotina = System.currentTimeMillis() - inicio;

        assertThat(duracaoPrioritaria)
                .as("faixa prioritaria concluiu antes da rotina, mesmo com a rotina ocupada com um lote grande")
                .isLessThan(duracaoRotina);
        assertThat(duracaoPrioritaria)
                .as("faixa prioritaria nao pode ter ficado presa atras da rotina")
                .isLessThan(10_000);

        Integer pontosRotina = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ponto_dado_mercado WHERE chave_instrumento LIKE 'LANE_ROTINA_%'",
                Integer.class);
        assertThat(pontosRotina).isEqualTo(1500);
    }
}
