import { app, HttpResponseInit } from "@azure/functions";

const RESPONSE_HEADERS = {
  "Content-Type": "application/json; charset=utf-8",
  "X-Content-Type-Options": "nosniff",
  "Cache-Control": "no-store",
};

const APPLICATION_STATE = {
  isHealthy: true,
};

export function setApplicationHealth(healthy: boolean): void {
  APPLICATION_STATE.isHealthy = healthy;
}

export async function healthCheckHandler(): Promise<HttpResponseInit> {
  const statusCode = APPLICATION_STATE.isHealthy ? 200 : 503;
  const statusMessage = APPLICATION_STATE.isHealthy ? "UP" : "Down";

  return {
    status: statusCode,
    headers: RESPONSE_HEADERS,
    jsonBody: {
      status: statusMessage,
      service: "acts-fun-curvas-conector",
      timestamp: new Date().toISOString(),
    },
  };
}

app.http("healthCheck", {
  methods: ["GET"],
  authLevel: "anonymous",
  route: "health",
  handler: healthCheckHandler,
});
