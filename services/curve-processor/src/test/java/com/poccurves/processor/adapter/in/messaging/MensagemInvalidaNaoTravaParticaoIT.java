package com.poccurves.processor.adapter.in.messaging;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.serialization.StringDeserializer;
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
import java.util.List;
import java.util.Properties;
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
 * Cobre as tarefas 8.20 e 8.21 do backlog juntas, publicando uma mensagem
 * genuinamente invalida seguida de uma mensagem valida no mesmo topico real
 * (marketdata.rotina.v1) e observando o resultado por dois angulos:
 * <p>
 * 8.20 — a mensagem invalida deve terminar na dead-letter real com os 9
 * cabecalhos {@code x-*} obrigatorios do catalogo
 * (contracts/events/topics.yaml), verificado lendo de volta um registro real
 * do topico de dead-letter. Ja havia sido verificado manualmente nesta
 * sessao; aqui fica automatizado.
 * <p>
 * 8.21 — "falha de desserializacao nao trava a particao": com
 * {@code StringDeserializer} (via {@code ErrorHandlingDeserializer} como
 * defesa em profundidade), praticamente nao existe sequencia de bytes que
 * falhe a desserializacao de fato — qualquer array de bytes vira uma String
 * (decodificacao UTF-8 lenient, sem excecao). A falha real e sempre
 * descoberta depois, na validacao do envelope (JSON invalido ou fora do
 * schema), e traz o mesmo resultado pratico que a tarefa pede: uma mensagem
 * que o processor nao consegue interpretar nao pode travar a particao. Esta
 * classe prova exatamente essa propriedade — a mensagem valida publicada
 * logo apos a invalida e consumida sem esperar por nenhum timeout de
 * retentativa (a excecao de envelope invalido nao e retentavel — vai direto
 * para a dead-letter).
 */
@SpringBootTest(classes = CurveProcessorApplication.class)
class MensagemInvalidaNaoTravaParticaoIT {

    private static final String TOPICO_ROTINA = "marketdata.rotina.v1";
    private static final String TOPICO_DLQ_ROTINA = "marketdata.rotina.v1.curve-processor-rotina.dlq";

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
        sa.update("DELETE FROM ponto_dado_mercado WHERE chave_instrumento LIKE 'DLQISOL_%'");
        sa.update("DELETE FROM lote_ingestao WHERE lote_externo_id LIKE 'it-dlqisol-%'");
    }

    @AfterEach
    void depois() {
        limparDadosDeTeste();
    }

    private String envelopeValido(String loteId, String ticker) {
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
                  "sequencia": 1,
                  "totalBlocos": 1,
                  "payload": {
                    "sourceUrl": "file://teste",
                    "encoding": "UTF-8",
                    "contentHash": "sha256:%s",
                    "sizeBytes": 10,
                    "records": [{"raw": "%s"}]
                  }
                }
                """.formatted(UUID.randomUUID(), UUID.randomUUID(), loteId, "a".repeat(64), fragmento);
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

    private ConsumerRecord<String, String> lerMensagemDlqPorChave(String topicoDlq, String chaveEsperada, Duration tempoMaximo) {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:19092");
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "it-dlq-check-" + UUID.randomUUID());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);

        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(List.of(topicoDlq));
            Instant limite = Instant.now().plus(tempoMaximo);
            while (Instant.now().isBefore(limite)) {
                ConsumerRecords<String, String> registros = consumer.poll(Duration.ofMillis(500));
                for (ConsumerRecord<String, String> registro : registros) {
                    if (chaveEsperada.equals(registro.key())) {
                        return registro;
                    }
                }
            }
            return null;
        }
    }

    @Test
    void mensagemInvalidaVaiParaDeadLetterSemTravarConsumoDaMensagemSeguinte() throws Exception {
        String chavePoison = "poison-" + UUID.randomUUID();
        String mensagemInvalida = "{ isto nao eh JSON valido de jeito nenhum";

        String loteValido = "it-dlqisol-" + UUID.randomUUID();
        String envelopeValido = envelopeValido(loteValido, "DLQISOL_OK");

        kafkaTemplate.send(TOPICO_ROTINA, chavePoison, mensagemInvalida).get(10, TimeUnit.SECONDS);
        kafkaTemplate.send(TOPICO_ROTINA, loteValido, envelopeValido).get(10, TimeUnit.SECONDS);

        // 8.21: a mensagem valida publicada logo apos a invalida e processada sem
        // esperar nenhum timeout de retentativa — a particao nao travou.
        aguardarAte(() -> existePonto("DLQISOL_OK"), Duration.ofSeconds(15));

        // 8.20: a mensagem invalida foi recuperada para a dead-letter com os 9
        // cabecalhos x-* obrigatorios do catalogo (contracts/events/topics.yaml).
        ConsumerRecord<String, String> registroDlq =
                lerMensagemDlqPorChave(TOPICO_DLQ_ROTINA, chavePoison, Duration.ofSeconds(15));

        assertThat(registroDlq)
                .as("mensagem invalida deveria ter chegado na dead-letter " + TOPICO_DLQ_ROTINA)
                .isNotNull();

        Headers headers = registroDlq.headers();
        List<String> cabecalhosObrigatorios = List.of(
                "x-dlq-reason", "x-dlq-detail", "x-correlation-id", "x-event-id",
                "x-original-topic", "x-original-partition", "x-original-offset",
                "x-failed-at", "x-attempts");
        for (String nome : cabecalhosObrigatorios) {
            assertThat(headers.lastHeader(nome))
                    .as("cabecalho obrigatorio ausente na dead-letter: " + nome)
                    .isNotNull();
        }

        String topicoOriginal = new String(headers.lastHeader("x-original-topic").value(), java.nio.charset.StandardCharsets.UTF_8);
        assertThat(topicoOriginal).isEqualTo(TOPICO_ROTINA);
    }
}
