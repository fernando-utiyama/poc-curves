jest.mock("axios", () => ({
  get: jest.fn(),
  isAxiosError: (error: unknown) =>
    Boolean(error && typeof error === "object" && "isAxiosError" in error),
}));

import axios from "axios";
import {
  buildSwapExFileName,
  buildBlobDateFolder,
  downloadSwapExFile,
  downloadSwapExFileForDate,
} from "../../src/services/b3/b3DownloadService";

const mockedAxios = axios as any;

describe("b3DownloadService", () => {
  const originalEnv = process.env;

  beforeEach(() => {
    jest.clearAllMocks();
    process.env = { ...originalEnv };
    delete process.env.B3_LOCAL_FILE_ENABLED;
    delete process.env.B3_SWAP_EX_LOCAL_FILE;
    delete process.env.B3_SWAP_EX_URL;
  });

  afterAll(() => {
    process.env = originalEnv;
  });

  it("deve montar o nome do arquivo no formato TSyymmdd.ex_", () => {
    const fileName = buildSwapExFileName(new Date(year: 2026, monthIndex: 8, date: 8));
    expect(fileName).toBe("TS260908.ex_");
  });

  it("deve montar a pasta do dia no formato yyyyMMdd", () => {
    const folder = buildBlobDateFolder(new Date(year: 2026, monthIndex: 8, date: 8));
    expect(folder).toBe("20260908");
  });

  it("deve usar o dia corrente no fuso da B3", () => {
    const instant = new Date(value: "2026-09-09T01:00:00.000Z");

    expect(buildSwapExFileName(instant)).toBe("TS260908.ex_");
    expect(buildBlobDateFolder(instant)).toBe("20260908");
  });

  it("deve baixar o arquivo .ex_ como Buffer via HTTPS", async () => {
    const payload: any = Buffer.from("conteudo-binario");

    mockedAxios.get.mockResolvedValueOnce({
      status: 200,
      data: payload,
    } as any);

    const result: Buffer = await downloadSwapExFile(fileName: "TS260908.ex_");

    expect(mockedAxios.get).toHaveBeenCalledTimes(1);
    expect(mockedAxios.get).toHaveBeenCalledWith(
      "https://www.b3.com.br/pesquisaregiao/download?filelist=TS260908.ex_",
      expect.any(Object),
    );
    expect(Buffer.isBuffer(result)).toBe(true);
    expect(result.toString()).toBe("conteudo-binario");
  });

  it("deve ler arquivo local quando mock ativado", async () => {
    process.env.B3_LOCAL_FILE_ENABLED = "true";
    process.env.B3_SWAP_EX_LOCAL_FILE = "test/b3/fixtures/TS260908.ex_";

    const result: Buffer = await downloadSwapExFile();

    expect(mockedAxios.get).not.toHaveBeenCalled();
    expect(Buffer.isBuffer(result)).toBe(true);
    expect(result.length).toBeGreaterThan(0);
  });

  it("deve lançar erro no modo mock sem arquivo configurado", async () => {
    process.env.B3_LOCAL_FILE_ENABLED = "true";

    await expect(downloadSwapExFile()).rejects.toThrow(
      /B3_SWAP_EX_LOCAL_FILE/,
    );
  });

  it("deve buscar o último dia útil quando a B3 retornar 404", async () => {
    const notFound: Error & (isAxiosError: true) = Object.assign(new Error("not found"), {
      isAxiosError: true,
      response: { status: 404 },
    });
    mockedAxios.get.mockRejectedValueOnce(notFound);

    await expect(downloadSwapExFileForDate(
      new Date(value: "2026-09-07T12:00:00.000Z"),
    )).rejects.toThrow(/Nenhum arquivo .ex_ encontrado/);
    expect(mockedAxios.get).toHaveBeenNthCalledWith(1,
      "https://www.b3.com.br/pesquisaregiao/download?filelist=TS260908.ex_",
    );
    expect(mockedAxios.get).toHaveBeenNthCalledWith(2,
      "https://www.b3.com.br/pesquisaregiao/download?filelist=TS260904.ex_",
    );
  });

  it("deve preservar query existente na URL configurada", async () => {
    process.env.B3_SWAP_EX_URL = "https://proxy.example/download?tenant=b3&filelist=TS260908.ex_";
    mockedAxios.get.mockResolvedValueOnce({
      status: 200,
      data: Buffer.from("conteudo"),
    } as any);

    const result: Buffer = await downloadSwapExFile(fileName: "TS260908.ex_");

    expect(mockedAxios.get).toHaveBeenCalledWith(
      "https://proxy.example/download?tenant=b3&filelist=TS260908.ex_",
      expect.any(Object),
    );
  });

  it.each([
    ["https://proxy.example/download", "/URL de download inválida"],
    ["url-invalida", "/URL de download inválida"],
  ])("deva rejeitar URL de download inválida: %s", async (url: any, error: any) => {
    process.env.B3_SWAP_EX_URL = url;

    await expect(downloadSwapExFile(fileName: "TS260908.ex_")).rejects.toThrow(error);
  });

  it("deva falhar após esgotar a busca por arquivos não encontrados", async () => {
    const notFound: Error & (isAxiosError: true) = Object.assign(new Error("not found"), {
      isAxiosError: true,
      response: { status: 404 },
    });
    mockedAxios.get.mockRejectedValue(notFound);

    await expect(
      downloadSwapExFileForDate(new Date(value: "2026-09-07T12:00:00.000Z")),
    ).rejects.toThrow(/Nenhum arquivo .ex_ encontrado/);
    expect(mockedAxios.get).toHaveBeenCalledTimes(1);
  });
});
