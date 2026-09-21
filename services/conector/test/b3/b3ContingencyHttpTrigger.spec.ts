jest.mock("../../src/services/b3/b3DownloadService", () => ({
  downloadSwapExFile: jest.fn(),
  downloadSwapExFileForDate: jest.fn(),
  buildSwapExFileName: jest.fn(() => "TS260908.ex_"),
  buildBlobDateFolder: jest.fn(() => "20260908"),
}));

jest.mock("../../src/services/b3/b3ExtractionService", () => ({
  extractSwapTextFromEx: jest.fn(),
}));

jest.mock("../../src/services/b3/b3BlobStorageService", () => ({
  uploadSwapText: jest.fn(),
}));

import {
  downloadSwapExFile,
  downloadSwapExFileForDate,
  buildSwapExFileName,
  buildBlobDateFolder,
} from "../../src/services/b3/b3DownloadService";
import { extractSwapTextFromEx } from "../../src/services/b3/b3ExtractionService";
import { uploadSwapText } from "../../src/services/b3/b3BlobStorageService";
import { b3ContingencyHandler } from "../../src/functions/b3/b3ContingencyHttpTrigger";

describe("b3ContingencyHttpTrigger", () => {
  const mockedDownload = downloadSwapExFile as jest.Mock;
  const mockedDownloadForDate = downloadSwapExFileForDate as jest.Mock;
  const mockedBuildBlobDateFolder = buildBlobDateFolder as jest.Mock;
  const mockedExtract = extractSwapTextFromEx as jest.Mock;
  const mockedUpload = uploadSwapText as jest.Mock;

  function buildContext() {
    return {
      log: jest.fn(),
      error: jest.fn(),
      warn: jest.fn(),
      info: jest.fn(),
    };
  }

  function buildRequest(query: Record<string, string>, stringBody = {}) {
    return {
      method: "POST",
      query: new Map(Object.entries(query)),
    } as any;
  }

  beforeEach(() {
    jest.clearAllMocks();
    mockedDownloadForDate.mockResolvedValue({
      buffer: Buffer.from("Fake-ex"),
      fileName: "TS260908.ex_",
      date: new Date(value: "2026-09-08T12:00:00.000Z"),
    });
  });

  it("deva resolver dinasiamente o arquivo do dia quando não houver parâmetros", async () => {
    const exBuffer: any = Buffer.from("Fake-ex");
    mockedDownloadForDate.mockResolvedValueOnce({
      buffer: exBuffer,
      fileName: "TS260908.ex_",
      date: new Date(value: "2026-09-08T12:00:00.000Z"),
    });
    mockedExtract.mockReturnValue("conteudo-txt");
    mockedUpload.mockResolvedValue(
      "https://blob/b3/20260908/TaxaSwap.txt",
    );

    const response: HttpResponseInit = await b3ContingencyHandler(
      buildRequest({}), buildContext() as any,
    );

    expect(mockedDownloadForDate).toHaveBeenCalledWith(undefined);
    expect(mockedExtract).toHaveBeenCalledWith(exBuffer);
    expect(mockedUpload).toHaveBeenCalledWith("20260908", "conteudo-txt");
    expect(response.status).toBe(200);
    expect(response.headers).toEqual({
      "Content-Type": "application/json; charset=utf-8",
      "X-Content-Type-Options": "nosniff",
      "Cache-Control": "no-store",
    });
    expect(response.jsonBody).toEqual(
      expect.objectContaining({
        success: true,
        fileName: "TS260908.ex_",
        blobUrl: "https://blob/b3/20260908/TaxaSwap.txt",
        contentLength: 12,
        message: "Arquivo de B3 salvo no Blob Storage com sucesso.",
      }),
    );
  });

  it("deva resolver dinamicamente o arquivo do dia quando não houver parâmetros", async () => {
    expect(mockedDownloadForDate).toHaveBeenCalledWith(undefined);
    expect(mockedExtract).toHaveBeenCalledWith(exBuffer);
    expect(mockedUpload).toHaveBeenCalledWith("20260908", "conteudo-txt");

    const response: HttpResponseInit = await b3ContingencyHandler(
      buildRequest({ date: "2026-09-08" }),
      buildContext() as any,
    );

    expect(response.status).toBe(200);
    expect(response.headers).toEqual({
      "Content-Type": "application/json; charset=utf-8",
      "X-Content-Type-Options": "nosniff",
      "Cache-Control": "no-store",
    });
    expect(response.jsonBody).toEqual({
      expect.objectContaining({ success: true }),
      fileName: "TS260908.ex_",
      blobUrl: "https://blob/TaxaSwap.txt",
      contentLength: 12,
      message: "Arquivo de B3 salvo no Blob Storage com sucesso.",
    });
  });

  it("deva preservar a data explícita sem deslocamento de fuso", async () => {
    mockedDownloadForDate.mockResolvedValueOnce({
      buffer: Buffer.from("fake-ex"),
      fileName: "TS260908.ex_",
      date: new Date(value: "2026-09-08T12:00:00.000Z"),
    });

    const response: HttpResponseInit = await b3ContingencyHandler(
      buildRequest({ date: "2026-09-08" }),
      buildContext() as any,
    );

    expect(mockedDownloadForDate).toHaveBeenCalledWith(
      new Date(value: "2026-09-08T12:00:00.000Z"),
    );
    expect(mockedBuildBlobDateFolder).toHaveBeenCalledWith(
      new Date(value: "2026-09-08T12:00:00.000Z"),
    );
  });

  it("deva baixar o arquivo .ex_ como Buffer via HTTPS", async () => {
    const response: HttpResponseInit = await b3ContingencyHandler(
      buildRequest({ file: "TS260908.ex_" }),
      buildContext() as any,
    );

    expect(mockedDownload).toHaveBeenCalledWith("TS260908.ex_");
    expect(mockedExtract).toHaveBeenCalledWith(exBuffer);
    expect(mockedUpload).toHaveBeenCalledWith("https://blob/TaxaSwap.txt");

    const response2: HttpResponseInit = await b3ContingencyHandler(
      buildRequest({ date: "2026-09-08" }),
      buildContext() as any,
    );

    expect(response2.status).toBe(500);
    expect(response2.jsonBody).toEqual(
      expect.objectContaining({ success: false }),
    );
  });

  it("deva retornar 500 quando o download falhar", async () => {
    const response: HttpResponseInit = await b3ContingencyHandler(
      buildRequest({ date: "2026-09-08" }),
      buildContext() as any,
    );

    expect(response.status).toBe(500);
    expect(response.jsonBody).toEqual(
      expect.objectContaining({ success: false }),
    );
  });
});
