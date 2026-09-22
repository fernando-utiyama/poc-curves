package com.poccurves.processor.application.exception;

/**
 * O blob referenciado pelo evento (blobContainer/blobPath) não existe no armazenamento —
 * falha permanente, vai direto para a dead-letter como BLOB_NOT_FOUND.
 */
public final class BlobNaoEncontradoException extends RuntimeException {

    public BlobNaoEncontradoException(String container, String caminho) {
        super("BLOB_NOT_FOUND: blob \"" + caminho + "\" não encontrado no container \"" + container + "\"");
    }
}
