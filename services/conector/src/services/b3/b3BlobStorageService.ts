import { DefaultAzureCredential } from "@azure/identity";
import { BlobServiceClient, ContainerClient } from "@azure/storage-blob";

let containerClient: ContainerClient | null = null;
let cachedContainerName: string | null = null;

function getContainerClient(): ContainerClient {
  const containerName = process.env.B3_BLOB_CONTAINER;

  if (!containerName) {
    throw new Error(
      "[b3BlobStorageService] Variável de ambiente B3_BLOB_CONTAINER não configurada.",
    );
  }

  if (containerClient && cachedContainerName === containerName) {
    return containerClient;
  }

  const connectionString = process.env.B3_BLOB_CONNECTION_STRING;
  const accountUrl = process.env.B3_BLOB_ACCOUNT_URL;

  let serviceClient: BlobServiceClient;

  if (connectionString) {
    serviceClient = BlobServiceClient.fromConnectionString(connectionString);
  } else if (accountUrl) {
    serviceClient = new BlobServiceClient(accountUrl, new DefaultAzureCredential());
  } else {
    throw new Error(
      "[b3BlobStorageService] Configure B3_BLOB_CONNECTION_STRING ou B3_BLOB_ACCOUNT_URL.",
    );
  }

  containerClient = serviceClient.getContainerClient(containerName);
  cachedContainerName = containerName;

  return containerClient;
}

/**
 * Grava o texto do swap extraído no Blob Storage no caminho
 * `b3/{dateFolder}/{fileName}` e retorna a URL do blob.
 *
 * @param dateFolder Pasta do dia (ex.: `20260908`).
 * @param content Conteúdo texto do arquivo TaxaSwap.
 * @param fileName Nome do arquivo (default: `TaxaSwap.txt`).
 */
export async function uploadSwapText(
  dateFolder: string,
  content: string,
  fileName = "TaxaSwap.txt",
): Promise<string> {
  const client = getContainerClient();
  const blobName = `b3/${dateFolder}/${fileName}`;
  const blockBlobClient = client.getBlockBlobClient(blobName);

  const buffer = Buffer.from(content, "latin1");

  await blockBlobClient.upload(buffer, buffer.length, {
    blobHTTPHeaders: {
      blobContentType: "text/plain; charset=iso-8859-1",
    },
  });

  return blockBlobClient.url;
}

export function clearBlobClientCache(): void {
  containerClient = null;
  cachedContainerName = null;
}
