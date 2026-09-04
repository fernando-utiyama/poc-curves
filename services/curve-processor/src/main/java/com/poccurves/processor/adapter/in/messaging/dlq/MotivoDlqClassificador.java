package com.poccurves.processor.adapter.in.messaging.dlq;

import com.poccurves.processor.domain.CurvaNaoMapeadaException;
import com.poccurves.processor.domain.CurvaVaziaException;
import com.poccurves.processor.domain.DatasetDesconhecidoException;
import com.poccurves.processor.domain.EnvelopeInvalidoException;
import com.poccurves.processor.domain.IncoerenciaModoOrigemException;
import com.poccurves.processor.domain.ParseFalhouException;
import org.springframework.kafka.support.serializer.DeserializationException;

/**
 * Classifica a exceção de falha numa categoria resumida — campo
 * {@code x-dlq-reason} do catálogo (contracts/events/topics.yaml, seção
 * "CABEÇALHOS OBRIGATÓRIOS EM MENSAGENS ENCAMINHADAS À DLQ"), que dá os
 * exemplos DESERIALIZATION_ERROR, VALIDATION_ERROR e TIMEOUT.
 */
public final class MotivoDlqClassificador {

    private MotivoDlqClassificador() {
    }

    public static String classificar(Throwable exception) {
        Throwable causa = causaRaiz(exception);
        if (causa instanceof DeserializationException) {
            return "DESERIALIZATION_ERROR";
        }
        if (causa instanceof EnvelopeInvalidoException) {
            return "VALIDATION_ERROR";
        }
        if (causa instanceof DatasetDesconhecidoException) {
            return "UNKNOWN_DATASET";
        }
        if (causa instanceof ParseFalhouException) {
            return "PARSE_FAILED";
        }
        if (causa instanceof CurvaNaoMapeadaException) {
            return "UNMAPPED_CURVE";
        }
        if (causa instanceof CurvaVaziaException) {
            return "EMPTY_CURVE";
        }
        if (causa instanceof IncoerenciaModoOrigemException) {
            return "VALIDATION_ERROR";
        }
        return "PROCESSING_ERROR";
    }

    private static Throwable causaRaiz(Throwable exception) {
        // As exceções de negócio (EnvelopeInvalidoException etc.) chegam
        // aqui envoltas em ListenerExecutionFailedException — procura pela
        // primeira causa de um tipo que reconhecemos, sem descer ao infinito
        // em causas cíclicas (limite defensivo).
        Throwable atual = exception;
        for (int i = 0; i < 10 && atual != null; i++) {
            if (atual instanceof DeserializationException
                    || atual instanceof EnvelopeInvalidoException
                    || atual instanceof DatasetDesconhecidoException
                    || atual instanceof ParseFalhouException
                    || atual instanceof CurvaNaoMapeadaException
                    || atual instanceof CurvaVaziaException
                    || atual instanceof IncoerenciaModoOrigemException) {
                return atual;
            }
            atual = atual.getCause();
        }
        return exception;
    }
}
