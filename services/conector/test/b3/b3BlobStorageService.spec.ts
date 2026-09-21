const mockUpload = jest.fn();
const mockGetBlockBlobClient = jest.fn(() => ({
  upload: mockUpload,
  url: "https://storage.example/curvas/b3/20260908/TaxaSwap.txt",
}));
const mockGetContainerClient = jest.fn(() => ({
  getBlockBlobClient: mockGetBlockBlobClient,
}));
const mockFromConnectionString = jest.fn(() => ({
  getContainerClient: mockGetContainerClient,
}));
const mockBlobServiceClient = jest.fn(() => ({
  getContainerClient: mockGetContainerClient,
}));
Object.assign(mockBlobServiceClient, {
  fromConnectionString: mockFromConnectionString,
});

jest.mock("@azure/storage-blob", () => ({
  BlobServiceClient: mockBlobServiceClient,
}));

jest.mock("@azure/identity", () => ({
  DefaultAzureCredential: jest.fn(),
}));

// TODO: confirmar com o usuario - este import estava recolhido (folded) na
// IDE na foto (so aparecia "import ..."); reconstruido a partir do uso real
// abaixo (uploadSwapText, clearBlobClientCache) e do padrao de caminho
// relativo test/b3/ -> src/services/b3/ ja confirmado em outros arquivos.
import {
  uploadSwapText,
  clearBlobClientCache,
} from "../../src/services/b3/b3BlobStorageService";

describe("b3BlobStorageService", () => {
  const originalEnv = process.env;

  beforeEach(() => {
    process.env = {
      ...originalEnv,
      B3_BLOB_CONTAINER: "curvas",
      B3_BLOB_CONNECTION_STRING: "UseDevelopmentStorage=true",
    };
    clearBlobClientCache();
    jest.clearAllMocks();
    mockUpload.mockResolvedValue({});
  });

  afterAll(() => {
    process.env = originalEnv;
    clearBlobClientCache();
  });

  it("deve salvar o TaxaSwap.txt no caminho diário preservando Latin-1", async () => {
    const content = "CURVA AÇÃO";

    const url = await uploadSwapText("20260908", content);

    expect(mockFromConnectionString).toHaveBeenCalledWith("UseDevelopmentStorage=true");
    expect(mockGetContainerClient).toHaveBeenCalledWith("curvas");
    expect(mockGetBlockBlobClient).toHaveBeenCalledWith(
      "b3/20260908/TaxaSwap.txt",
    );
    expect(mockUpload).toHaveBeenCalledWith(
      Buffer.from(content, "latin1"),
      Buffer.byteLength(content, "latin1"),
      {
        blobHTTPHeaders: {
          blobContentType: "text/plain; charset=iso-8859-1",
        },
      },
    );
    expect(url).toBe("https://storage.example/curvas/b3/20260908/TaxaSwap.txt");
  });

  it("deve exigir o container do Blob", async () => {
    delete process.env.B3_BLOB_CONTAINER;

    await expect(uploadSwapText("20260908", "conteudo")).rejects.toThrow(
      /B3_BLOB_CONTAINER/,
    );
  });

  it("deve usar account URL e reutilizar o cliente em uploads sucessivos", async () => {
    delete process.env.B3_BLOB_CONNECTION_STRING;
    process.env.B3_BLOB_ACCOUNT_URL = "https://storage.example";

    await uploadSwapText("20260908", "primeiro", "custom.txt");
    await uploadSwapText("20260909", "segundo");

    expect(mockBlobServiceClient).toHaveBeenCalledTimes(1);
    expect(mockFromConnectionString).not.toHaveBeenCalled();
    expect(mockGetBlockBlobClient).toHaveBeenNthCalledWith(
      1,
      "b3/20260908/custom.txt",
    );
    expect(mockGetBlockBlobClient).toHaveBeenNthCalledWith(
      2,
      "b3/20260909/TaxaSwap.txt",
    );
  });

  it("deve exigir uma forma de conexão com o Blob", async () => {
    delete process.env.B3_BLOB_CONNECTION_STRING;
    delete process.env.B3_BLOB_ACCOUNT_URL;

    await expect(uploadSwapText("20260908", "conteudo")).rejects.toThrow(
      /B3_BLOB_CONNECTION_STRING ou B3_BLOB_ACCOUNT_URL/,
    );
  });
});
