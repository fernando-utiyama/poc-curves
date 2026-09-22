package com.poccurves.processor.adapter.out.blob;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.poccurves.processor.application.port.BlobStorageReadPort;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Implementação real de {@link BlobStorageReadPort} sobre o SDK oficial do Azure Blob Storage
 * (com.azure:azure-storage-blob) — funciona tanto contra Azurite (emulador local) quanto contra
 * o Azure real em produção, só a connection string muda.
 */
@Component
public class AzuriteBlobStorageAdapter implements BlobStorageReadPort {

    private final BlobServiceClient client;

    public AzuriteBlobStorageAdapter(@Value("${azurite.connection-string}") String connectionString) {
        this.client = new BlobServiceClientBuilder().connectionString(connectionString).buildClient();
    }

    @Override
    public boolean existe(String container, String caminho) {
        BlobClient blobClient = client.getBlobContainerClient(container).getBlobClient(caminho);
        return blobClient.exists();
    }

    @Override
    public byte[] baixar(String container, String caminho) {
        BlobClient blobClient = client.getBlobContainerClient(container).getBlobClient(caminho);
        return blobClient.downloadContent().toBytes();
    }
}
