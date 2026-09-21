import {
  app,
  HttpRequest,
  HttpResponseInit,
  InvocationContext,
} from "@azure/functions";
import { extractSwapTextFromEx } from "../../services/b3/b3ExtractionService";
import { uploadSwapText } from "../../services/b3/b3BlobStorageService";
import {
  buildBlobDateFolder,
  downloadSwapExFile,
  downloadSwapExFileForDate,
} from "../../services/b3/b3DownloadService";

const RESPONSE_HEADERS = {
  "Content-Type": "application/json; charset=utf-8",
  "X-Content-Type-Options": "nosniff",
  "Cache-Control": "no-store",
};

const SWAP_FILE_NAME_PATTERN = /^TS\d{6}\.ex_$/i;

function resolveRequestedDate(request: HttpRequest): Date | undefined {
  const dateParam = request.query.get("date");

  if (!dateParam) {
    return undefined;
  }

  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(dateParam);

  if (!match) {
    throw new Error("Parâmetro 'date' inválido. Use o formato YYYY-MM-DD.");
  }

  const [, year, month, day] = match;
  const parsed = new Date(Date.UTC(Number(year), Number(month) - 1, Number(day), 12));

  if (
    parsed.getUTCFullYear() !== Number(year) ||
    parsed.getUTCMonth() !== Number(month) - 1 ||
    parsed.getUTCDate() !== Number(day)
  ) {
    throw new Error("Parâmetro 'date' inválido. Use o formato YYYY-MM-DD.");
  }

  return parsed;
}

function resolveFileName(request: HttpRequest): string | undefined {
  const fileParam = request.query.get("file");

  if (fileParam) {
    if (!SWAP_FILE_NAME_PATTERN.test(fileParam)) {
      throw new Error("Parâmetro 'file' inválido. Esperado formato TSyymmdd.ex_.");
    }

    return fileParam;
  }

  return undefined;
}

export async function b3ContingencyHandler(
  request: HttpRequest,
  context: InvocationContext,
): Promise<HttpResponseInit> {
  try {
    context.log("[b3Contingency] Iniciando contingência real de swap B3.");

    const requestedDate = resolveRequestedDate(request);
    const fileName = resolveFileName(request);
    const downloaded = fileName
      ? {
          buffer: await downloadSwapExFile(fileName),
          fileName,
          date: requestedDate ?? new Date(),
        }
      : await downloadSwapExFileForDate(requestedDate);
    const dateFolder = buildBlobDateFolder(downloaded.date);
    const exBuffer = downloaded.buffer;
    context.log(
      `[b3Contingency] Arquivo ${downloaded.fileName} baixado (${exBuffer.length} bytes).`,
    );

    const rawContent = extractSwapTextFromEx(exBuffer);
    context.log(
      `[b3Contingency] TaxaSwap.txt extraído (${rawContent.length} caracteres).`,
    );

    const blobUrl = await uploadSwapText(dateFolder, rawContent);
    context.log(`[b3Contingency] Texto gravado no Blob: ${blobUrl}`);

    return {
      status: 200,
      headers: RESPONSE_HEADERS,
      jsonBody: {
        success: true,
        fileName: downloaded.fileName,
        blobUrl,
        contentLength: rawContent.length,
        message: "Arquivo da B3 salvo no Blob Storage com sucesso.",
      },
    };
  } catch (error) {
    const message = error instanceof Error ? error.message : "Erro inesperado";

    context.error("[b3Contingency] Falha no processamento de contingência", message);

    return {
      status: 500,
      headers: RESPONSE_HEADERS,
      jsonBody: {
        success: false,
        message: "Erro interno ao processar contingência.",
      },
    };
  }
}

app.http("b3ContingencyTrigger", {
  methods: ["GET", "POST"],
  authLevel: "anonymous",
  route: "swap-contingency",
  handler: b3ContingencyHandler,
});
