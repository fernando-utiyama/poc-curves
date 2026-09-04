package com.poccurves.orchestrator.adapter.in.messaging;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.poccurves.orchestrator.application.MaterializarPendenciaDlqUseCase;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.UUID;

/**
 * Consome todos os tópicos de dead-letter do catálogo e delega a materialização (idempotência +
 * gravação) ao caso de uso — tarefas 10.2 (consumo de todos os tópicos), 10.3 (materialização a
 * partir dos cabeçalhos, sem copiar o payload) e 10.4 (idempotência por id_evento, offset
 * confirmado só após a gravação) do backlog curve-orchestrator.
 */
@Component
public class DlqMaterializacaoListener {

    private static final Logger log = LoggerFactory.getLogger(DlqMaterializacaoListener.class);

    private final MaterializarPendenciaDlqUseCase materializarPendenciaDlqUseCase;
    private final ObjectMapper objectMapper;

    public DlqMaterializacaoListener(MaterializarPendenciaDlqUseCase materializarPendenciaDlqUseCase, ObjectMapper objectMapper) {
        this.materializarPendenciaDlqUseCase = materializarPendenciaDlqUseCase;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
            topics = {
                    "marketdata.rotina.v1.curve-processor-rotina.dlq",
                    "marketdata.prioritaria.v1.curve-processor-prioritaria.dlq",
                    "marketdata.massa.v1.curve-processor-massa.dlq",
                    "marketdata.normalized.v1.curve-orchestrator-normalized.dlq",
                    "curve.published.v1.curve-orchestrator-published.dlq"
            },
            containerFactory = "dlqListenerContainerFactory"
    )
    public void consumir(ConsumerRecord<String, String> record, Acknowledgment ack) {
        String idEvento = lerCabecalho(record, "x-event-id");
        if (idEvento == null || idEvento.isBlank() || "unknown".equals(idEvento)) {
            log.warn("Mensagem de dead-letter sem x-event-id válido, tópico={}, partição={}, offset={} — não materializada",
                    record.topic(), record.partition(), record.offset());
            ack.acknowledge();
            return;
        }

        String motivo = valorOuPadrao(lerCabecalho(record, "x-dlq-reason"), "DESCONHECIDO");
        String detalhe = lerCabecalho(record, "x-dlq-detail");
        UUID correlacaoId = parseUuidOuNulo(lerCabecalho(record, "x-correlation-id"));
        String topicoOrigem = valorOuPadrao(lerCabecalho(record, "x-original-topic"), record.topic());
        Integer particaoOrigem = parseIntOuNulo(lerCabecalho(record, "x-original-partition"));
        Long offsetOrigem = parseLongOuNulo(lerCabecalho(record, "x-original-offset"));
        Instant falhouEm = parseInstantOuAgora(lerCabecalho(record, "x-failed-at"));

        JsonNode corpo = parseJsonOuNulo(record.value());
        String fonte = campoTexto(corpo, "source");
        String conjuntoDados = campoTexto(corpo, "dataset");
        if (conjuntoDados == null) {
            conjuntoDados = campoTexto(corpo, "curveCode");
        }
        LocalDate dataReferencia = parseDataOuNula(campoTexto(corpo, "referenceDate"));

        try {
            materializarPendenciaDlqUseCase.materializar(
                    idEvento, correlacaoId, motivo, detalhe, fonte, conjuntoDados, dataReferencia,
                    topicoOrigem, particaoOrigem, offsetOrigem,
                    record.topic(), record.partition(), record.offset(),
                    falhouEm
            );
            log.info("Pendência de dead-letter materializada: idEvento={} motivo={} topicoOrigem={}",
                    idEvento, motivo, topicoOrigem);
        } catch (Exception e) {
            log.error("Falha ao materializar pendência de dead-letter, idEvento={}, tópico={}, offset={} — offset NÃO confirmado, será reprocessado",
                    idEvento, record.topic(), record.offset(), e);
            return; // não confirma o offset — mensagem será reentregue
        }

        ack.acknowledge();
    }

    private String lerCabecalho(ConsumerRecord<String, String> record, String nome) {
        Header header = record.headers().lastHeader(nome);
        return header != null ? new String(header.value(), StandardCharsets.UTF_8) : null;
    }

    private String valorOuPadrao(String valor, String padrao) {
        return (valor == null || valor.isBlank()) ? padrao : valor;
    }

    private UUID parseUuidOuNulo(String valor) {
        if (valor == null || valor.isBlank() || "unknown".equals(valor)) {
            return null;
        }
        try {
            return UUID.fromString(valor);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private Integer parseIntOuNulo(String valor) {
        try {
            return valor != null ? Integer.valueOf(valor) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Long parseLongOuNulo(String valor) {
        try {
            return valor != null ? Long.valueOf(valor) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Instant parseInstantOuAgora(String valor) {
        try {
            return valor != null ? Instant.parse(valor) : Instant.now();
        } catch (Exception e) {
            return Instant.now();
        }
    }

    private JsonNode parseJsonOuNulo(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(valor);
        } catch (Exception e) {
            return null;
        }
    }

    private String campoTexto(JsonNode no, String campo) {
        if (no == null) {
            return null;
        }
        JsonNode valor = no.get(campo);
        return (valor != null && !valor.isNull()) ? valor.asText() : null;
    }

    private LocalDate parseDataOuNula(String valor) {
        if (valor == null) {
            return null;
        }
        try {
            return LocalDate.parse(valor);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
