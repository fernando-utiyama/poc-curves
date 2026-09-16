package com.poccurves.processor.application.exception;

/**
 * O conteúdo lido do blob não bate com o contentHash declarado no evento — falha permanente,
 * vai direto para a dead-letter como BLOB_INTEGRITY_ERROR.
 */
public final class IntegridadeBlobException extends RuntimeException {

    public IntegridadeBlobException(String container, String caminho, String hashEsperado, String hashCalculado) {
        super("BLOB_INTEGRITY_ERROR: conteúdo do blob \"" + caminho + "\" no container \"" + container
                + "\" não bate com o hash declarado (esperado " + hashEsperado + ", calculado " + hashCalculado + ")");
    }
}
