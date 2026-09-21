import {
  app,
  HttpRequest,
  HttpResponseInit,
  InvocationContext,
} from "@azure/functions";
import { publishRecords } from "../../producer/b3/sendKafkaB3";
import { fetchSwapFile } from "../../services/b3/b3Service";
import { parseFile } from "../../services/b3/parserB3Service";

const RESPONSE_HEADERS = {
  "Content-Type": "application/json; charset=utf-8",
  "X-Content-Type-Options": "nosniff",
  "Cache-Control": "no-store",
};

export async function httpHandler(
  request: HttpRequest,
  context: InvocationContext,
): Promise<HttpResponseInit> {
  try {
    context.log("[httpTrigger] Iniciando processamento via HTTP.");

    const rawContent = await fetchSwapFile();
    context.log("[httpTrigger] Arquivo obtido com sucesso da origem B3.");

    const records = parseFile(rawContent);
    context.log(
      `[httpTrigger] Total de registros válidos processados: ${records.length}.`,
    );

    await publishRecords(records);
    context.log("[httpTrigger] Publicação no Kafka concluída com sucesso.");

    return {
      status: 200,
      headers: RESPONSE_HEADERS,
      jsonBody: {
        success: true,
        totalRecords: records.length,
        message: "Processamento concluído com sucesso.",
      },
    };
  } catch (error) {
    const message =
      error instanceof Error ? error.message : "Erro inesperado";

    context.error("Erro no httpTrigger", message);

    return {
      status: 500,
      headers: RESPONSE_HEADERS,
      jsonBody: {
        success: false,
        message: "Erro interno ao processar solicitação.",
      },
    };
  }
}

app.http("httpTrigger", {
  methods: ["GET", "POST"],
  authLevel: "anonymous",
  route: "swap-process",
  handler: httpHandler,
});
