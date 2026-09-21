const { Kafka } = require("kafkajs");

const defaultKafkaBrokers = "localhost:29092";

function readPositiveInteger(name, defaultValue) {
  const rawValue = process.env[name] || defaultValue;
  const value = Number(rawValue);

  if (!Number.isInteger(value) || value <= 0) {
    throw new Error(`${name} deve ser um inteiro positivo. Valor recebido: ${rawValue}`);
  }

  return value;
}

function readOptionalPositiveInteger(name) {
  if (!process.env[name]) {
    return null;
  }

  return readPositiveInteger(name, process.env[name]);
}

const config = {
  healthUrl: process.env.FUNCTION_HEALTH_URL || "http://localhost:7071/api/health",
  kafkaUiUrl: process.env.KAFKA_UI_URL || `http://localhost:${process.env.KAFKA_UI_PORT || "8080"}`,
  functionUrl:
    process.env.FUNCTION_URL || "http://localhost:7071/api/swap-process",
  kafkaTopic:
    process.env.KAFKA_TOPIC ||
    "acts-dev-invgua-tcen-precif-curvas-b3-pubsub-negocio-json",
  kafkaBrokers: (process.env.KAFKA_BROKERS || defaultKafkaBrokers)
    .split(",")
    .map((broker) => broker.trim())
    .filter(Boolean),
  kafkaClientId: process.env.KAFKA_CLIENT_ID || "func-swap-b3-docker-e2e",
  expectedMessages: readOptionalPositiveInteger("KAFKA_CONSUME_MAX_MESSAGES"),
  consumeTimeoutMs: readPositiveInteger("KAFKA_CONSUME_TIMEOUT_MS", "60000"),
  waitTimeoutMs: readPositiveInteger("E2E_WAIT_TIMEOUT_MS", "120000"),
};

const kafka = new Kafka({
  clientId: config.kafkaClientId,
  brokers: config.kafkaBrokers,
});
const crypto = require("crypto");

function log(message) {
  console.log(`\n[${new Date().toISOString()}] ${message}`);
}

function delay(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

async function waitForHttp(url, description) {
  const startedAt = Date.now();

  log(`Aguardando ${description}: ${url}`);

  while (Date.now() - startedAt < config.waitTimeoutMs) {
    try {
      const response = await fetch(url);

      if (response.ok) {
        log(`${description} disponível`);
        return;
      }
    } catch {
      // Keep waiting while Docker finishes starting the Function host.
    }

    await delay(3000);
  }

  throw new Error(`Timeout aguardando ${description}: ${url}`);
}

async function requestFunction(method) {
  const response = await fetch(config.functionUrl, {
    method,
    headers: method === "POST" ? { "Content-Type": "application/json" } : undefined,
    body: method === "POST" ? "{}" : undefined,
  });

  const text = await response.text();

  if (!response.ok) {
    throw new Error(`${method} ${config.functionUrl} retornou ${response.status}: ${text}`);
  }

  let body;

  try {
    body = JSON.parse(text);
  } catch {
    throw new Error(`${method} ${config.functionUrl} não retornou JSON válido: ${text}`);
  }

  if (body.success !== true) {
    throw new Error(`${method} ${config.functionUrl} retornou success diferente de true: ${text}`);
  }

  console.log(`${method} ${config.functionUrl}`);
  console.log(JSON.stringify(body, null, 2));

  return body;
}

function assertKafkaPayload(payload, index) {
  if (!payload || typeof payload !== "object" || Array.isArray(payload)) {
    throw new Error(`Mensagem Kafka ${index} não possui payload JSON object.`);
  }

  if (!payload.values || typeof payload.values !== "object" || Array.isArray(payload.values)) {
    throw new Error(`Mensagem Kafka ${index} não possui values válido.`);
  }

  const values = payload.values;
  const requiredNumberFields = [
    "diasCorridos",
    "diasUteis",
    "valor",
    "fatorDiario",
    "fatorAcumulado",
  ];

  if (typeof values.ticker !== "string" || !values.ticker.trim()) {
    throw new Error(`Mensagem Kafka ${index} não possui values.ticker válido.`);
  }

  if (typeof values.refDate !== "string" || !/^\d{4}-\d{2}-\d{2}$/.test(values.refDate)) {
    throw new Error(`Mensagem Kafka ${index} não possui values.refDate ISO válido.`);
  }

  for (const field of requiredNumberFields) {
    if (typeof values[field] !== "number" || !Number.isFinite(values[field])) {
      throw new Error(`Mensagem Kafka ${index} não possui values.${field} numérico válido.`);
    }
  }
}

function validateKafkaMessages(messages, expectedMessages, expectedUniquePayloads) {
  if (messages.length < expectedMessages) {
    throw new Error(
      `Foram consumida(s) ${messages.length} mensagem(ns), mas eram esperada(s) ${expectedMessages}.`,
    );
  }

  messages.forEach(assertKafkaPayload);

  const uniquePayloads = new Set(
    messages.map((message) => {
      const values = message.values;

      return `${values.ticker}|${values.refDate}|${values.diasCorridos}|${values.diasUteis}|${values.valor}|${values.fatorDiario}|${values.fatorAcumulado}`;
    }),
  );

  if (uniquePayloads.size < expectedUniquePayloads) {
    throw new Error(
      `Foram encontrado(s) ${uniquePayloads.size} payload(s) único(s), mas eram esperado(s) pelo menos ${expectedUniquePayloads}.`,
    );
  }
}

function parseKafkaMessage(message/*, partition */) {
  const rawValue = message.value ? message.value.toString("utf8") : "";
  let value = rawValue;

  try {
    value = JSON.parse(rawValue);
  } catch {
    // Keep raw value when the Kafka message is not JSON.
  }

  return value;
}

async function collectKafkaMessages(action) {
  const consumer = kafka.consumer({
    groupId: `${config.kafkaClientId}-${Date.now()}-${crypto.randomUUID()}`,
  });
  const messages = [];
  let expectedMessages = null;
  let expectedUniquePayloads = null;
  let timeoutId;
  let resolveDone;
  let rejectDone;

  const done = new Promise((resolve, reject) => {
    resolveDone = resolve;
    rejectDone = reject;
  });

  function completeIfReady() {
    if (expectedMessages !== null && messages.length >= expectedMessages) {
      resolveDone();
    }
  }

  await consumer.connect();
  await consumer.subscribe({ topic: config.kafkaTopic, fromBeginning: false });
  await consumer.run({
    eachMessage: async ({ /* partition, */ message }) => {
      messages.push(parseKafkaMessage(message));
      completeIfReady();
    },
  });

  log(`Aguardando mensagem(ns) novas em ${config.kafkaTopic}`);

  try {
    await delay(1000);

    const actionResult = await action();
    expectedMessages = config.expectedMessages || actionResult.expectedMessages;
    expectedUniquePayloads = actionResult.expectedUniqueMessages || Math.ceil(expectedMessages / 2);

    if (!Number.isInteger(expectedMessages) || expectedMessages <= 0) {
      throw new Error(`Quantidade esperada de mensagens inválida: ${expectedMessages}`);
    }

    if (!Number.isInteger(expectedUniquePayloads) || expectedUniquePayloads <= 0) {
      throw new Error(`Quantidade esperada de payloads únicos inválida: ${expectedUniquePayloads}`);
    }

    log(`Esperando ${expectedMessages} mensagem(ns) publicada(s) no Kafka`);
    timeoutId = setTimeout(() => {
      rejectDone(new Error(
        `Timeout aguardando Kafka registrar ${expectedMessages} mensagem(ns) publicada(s).`,
      ));
    }, config.consumeTimeoutMs);
    completeIfReady();
    await done;
  } finally {
    clearTimeout(timeoutId);
    await consumer.disconnect();
  }

  validateKafkaMessages(messages, expectedMessages, expectedUniquePayloads);

  return messages;
}

async function main() {
  log("Validando endpoints do ambiente Docker já iniciado");

  await waitForHttp(config.healthUrl, "health check da Function");
  await waitForHttp(config.kafkaUiUrl, "Kafka UI");

  const messages = await collectKafkaMessages(async () => {
    log("Disparando endpoints GET e POST da Function");
    const getResponse = await requestFunction("GET");
    const postResponse = await requestFunction("POST");

    return {
      expectedMessages: Number(getResponse.totalRecords || 0) + Number(postResponse.totalRecords || 0),
      expectedUniqueMessages: Math.max(
        Number(getResponse.totalRecords || 0),
        Number(postResponse.totalRecords || 0),
      ),
    };
  });

  log("Mensagens recebidas no Kafka");
  for (const message of messages) {
    console.log(JSON.stringify(message, null, 2));
  }

  log("Cenário Docker E2E validado com sucesso");
  console.log(`Health: ${config.healthUrl}`);
  console.log(`Function: ${config.functionUrl}`);
  console.log(`Kafka UI: ${config.kafkaUiUrl}`);
  console.log(`Kafka brokers: ${config.kafkaBrokers.join(",")}`);
  console.log(`Kafka topic: ${config.kafkaTopic}`);
}

main().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
