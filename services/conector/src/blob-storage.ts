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
  private readonly containersConfirmados = new Set<string>();

  constructor(connectionString: string) {
    this.client = BlobServiceClient.fromConnectionString(connectionString);
  }

  async gravar(container: string, caminhoBlob: string, conteudo: Buffer): Promise<void> {
    // Nome de container do Azure Blob Storage exige no mínimo 3 caracteres —
    // um feeder com um container curto (achado real desta sessão: "b3", 2
    // caracteres) só falha aqui, em runtime, contra o serviço real; nenhum
    // teste unitário com fake pega isso. Falha cedo, com uma mensagem que
    // aponta a causa, em vez do RestError genérico do SDK.
    if (container.length < 3) {
      throw new Error(
        `nome de container de blob inválido: "${container}" tem ${container.length} caractere(s), mínimo exigido pelo Azure é 3`,
      );
    }
    const containerClient = this.client.getContainerClient(container);
    if (!this.containersConfirmados.has(container)) {
      await containerClient.createIfNotExists();
      this.containersConfirmados.add(container);
    }
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
