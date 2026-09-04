package com.poccurves.processor.domain.parsing;

/**
 * O DatasetParser registrado retornou {@code ParseResult.Falha} para o
 * bloco. Falha permanente — o conteúdo do bloco não muda numa retentativa,
 * vai direto para a dead-letter como PARSE_FAILED.
 */
public final class ParseFalhouException extends RuntimeException {

    public ParseFalhouException(String motivo, String diagnostico) {
        super("PARSE_FAILED: " + motivo + (diagnostico == null || diagnostico.isBlank() ? "" : " (" + diagnostico + ")"));
    }
}
