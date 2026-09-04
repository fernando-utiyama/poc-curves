package com.poccurves.orchestrator.application;

import com.poccurves.orchestrator.domain.PendenciaDlq;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Materializa uma mensagem de dead-letter como uma linha em pendencia_dlq — tarefas 10.2
 * (consumo de todos os tópicos), 10.3 (materialização a partir dos cabeçalhos, sem copiar o
 * payload) e 10.4 (idempotência por id_evento) do backlog curve-orchestrator.
 * <p>
 * Extraído do listener Kafka: a decisão de negócio (idempotência, montagem da pendência) não
 * deve depender de {@code ConsumerRecord}/{@code Header} — esses tipos ficam no adaptador, que já
 * chega aqui com valores desserializados.
 */
public class MaterializarPendenciaDlqUseCase {

    private final PendenciaDlqRepositoryPort pendenciaDlqRepository;

    public MaterializarPendenciaDlqUseCase(PendenciaDlqRepositoryPort pendenciaDlqRepository) {
        this.pendenciaDlqRepository = pendenciaDlqRepository;
    }

    /** @return true se materializou (ou já estava materializada); false se falhou e o offset não deve ser confirmado */
    public boolean materializar(
            String idEvento,
            UUID correlacaoId,
            String motivo,
            String detalhe,
            String fonte,
            String conjuntoDados,
            LocalDate dataReferencia,
            String topicoOrigem,
            Integer particaoOrigem,
            Long offsetOrigem,
            String topicoDlq,
            Integer particaoDlq,
            Long offsetDlq,
            Instant falhouEm
    ) {
        if (pendenciaDlqRepository.buscarPorIdEvento(idEvento).isPresent()) {
            // Já materializada (idempotência — tarefa 10.4): confirma o offset e segue.
            return true;
        }

        PendenciaDlq pendencia = PendenciaDlq.abrir(
                idEvento, correlacaoId, motivo, detalhe, fonte, conjuntoDados, dataReferencia,
                topicoOrigem, particaoOrigem, offsetOrigem,
                topicoDlq, particaoDlq, offsetDlq,
                null, null, falhouEm
        );
        pendenciaDlqRepository.inserir(pendencia);
        return true;
    }
}
