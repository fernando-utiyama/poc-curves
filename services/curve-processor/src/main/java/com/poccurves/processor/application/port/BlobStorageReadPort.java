package com.poccurves.processor.application.port;

/**
 * Leitura de blob storage (openspec/changes/raw-file-blob-storage) — os feeders de arquivo
 * gravam o conteúdo bruto adquirido em blob antes de publicar; o processor lê daqui antes de
 * invocar o parser do dataset correspondente.
 */
public interface BlobStorageReadPort {

    /** @return true se o blob existir no container/caminho informados */
    boolean existe(String container, String caminho);

    /** @return o conteúdo bruto do blob, como bytes — sem nenhuma conversão de encoding */
    byte[] baixar(String container, String caminho);
}
