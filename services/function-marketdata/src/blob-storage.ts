import { BlobServiceClient } from '@azure/storage-blob';

/**
 * Abstração mínima de gravação em blob storage, para permitir injeção de um
 * fake nos testes dos feeders — mesmo princípio de FetchLike em
 * http-client.ts.
 */
export interface BlobUploader {
  gravar(container: string, caminhoBlob: string, conteudo: Buffer): Promise<void>;
}

/**
 * Implementação real sobre o SDK oficial do Azure Blob Storage — funciona
 * tanto contra Azurite (emulador local) quanto contra o Azure real em
 * produção, só a connection string muda.
 */
export class AzureBlobUploader implements BlobUploader {
  private readonly client: BlobServiceClient;

  constructor(connectionString: string) {
    this.client = BlobServiceClient.fromConnectionString(connectionString);
  }

  async gravar(container: string, caminhoBlob: string, conteudo: Buffer): Promise<void> {
    const containerClient = this.client.getContainerClient(container);
    await containerClient.createIfNotExists();
    const blockBlobClient = containerClient.getBlockBlobClient(caminhoBlob);
    await blockBlobClient.upload(conteudo, conteudo.length);
  }
}

/**
 * Monta o caminho do blob dentro do container a partir da data de referência
 * e do nome do arquivo original — convenção <data-referencia>/<nome-arquivo>
 * (o container em si já representa a fonte, ex. "b3-raw", "anbima").
 */
export function montarCaminhoBlob(dataReferencia: string, nomeArquivo: string): string {
  return `${dataReferencia}/${nomeArquivo}`;
}
