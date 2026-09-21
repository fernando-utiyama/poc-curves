import { CompressionTypes, Kafka, Producer, RecordMetadata } from "kafkajs";
import { SwapRecord } from "../../models/b3/swapB3Record";
import { getSecret } from "../../services/b3/keyVaultService";
import { toKafkaSwapMessages } from "./swapB3MessageMapper";
import { toKafkaGroupedSwapMessages } from "./swapB3GroupMessageMapper";

let producer: Producer | null = null;

function isDisconnectedError(error: unknown): boolean {
  return error instanceof Error && /disconnected/i.test(error.message);
}

function numberFromEnv(name: string): number | undefined {
  const value = process.env[name];

  if (!value) {
    return undefined;
  }

  const parsed = Number(value);

  return Number.isFinite(parsed) ? parsed : undefined;
}

function formatError(error: unknown): Record<string, unknown> {
  if (error instanceof Error) {
    return {
      name: error.name,
      message: error.message,
    };
  }

  return {
    message: String(error),
  };
}

function parseMessageValue(value: string): unknown {
  try {
    return JSON.parse(value);
  } catch {
    return value;
  }
}

function logKafkaError(
  stage: "connect" | "send",
  error: unknown,
  details: Record<string, unknown>,
): void {
  console.error(
    `[kafkaService] Falha na etapa ${stage} da publicação Kafka`,
    JSON.stringify({
      ...details,
      error: formatError(error),
    }),
  );
}

function logKafkaMessages(
  topic: string,
  messages: Array<{ key: string; value: string }>,
): void {
  if (process.env.LOG_KAFKA_MESSAGES !== "true") {
    return;
  }

  console.info(
    `[kafkaService] Publicando ${messages.length} mensagem(ns) no tópico ${topic}.`,
  );

  messages.forEach((message, index) => {
    console.info(
      "[kafkaService] Mensagem Kafka",
      JSON.stringify({
        index,
        topic,
        key: message.key,
        value: parseMessageValue(message.value),
      }),
    );
  });
}

function logKafkaMetadata(metadata: RecordMetadata[]): void {
  if (process.env.LOG_KAFKA_MESSAGES !== "true") {
    return;
  }

  console.info(
    "[kafkaService] Metadados da publicação Kafka",
    JSON.stringify(metadata),
  );
}

async function resetProducer(): Promise<void> {
  if (!producer) {
    return;
  }

  const currentProducer = producer;
  producer = null;

  try {
    await currentProducer.disconnect();
  } catch {
    // Producer already disconnected. Keep singleton clean for next retry.
  }
}

async function getProducer(): Promise<Producer> {
  if (producer) {
    return producer;
  }

  const brokers = process.env.KAFKA_BROKERS?.split(",")
    .map((item) => item.trim())
    .filter(Boolean);

  const clientId = process.env.KAFKA_CLIENT_ID;

  if (!brokers || brokers.length === 0) {
    throw new Error("Variável de ambiente KAFKA_BROKERS não configurada.");
  }

  if (!clientId) {
    throw new Error("Variável de ambiente KAFKA_CLIENT_ID não configurada.");
  }

  const retry: Record<string, number> = {};
  const retries = numberFromEnv("KAFKA_RETRY_RETRIES");
  const initialRetryTime = numberFromEnv("KAFKA_RETRY_INITIAL_MS");

  if (retries !== undefined) {
    retry.retries = retries;
  }

  if (initialRetryTime !== undefined) {
    retry.initialRetryTime = initialRetryTime;
  }

  async function loadKafkaCredentials(): Promise<{
    username?: string | null;
    password?: string | null;
  }> {
    const kvName = process.env.KEY_VAULT_NAME;
    const keySecretName = process.env.KAFKA_KEY_SECRET_NAME;
    const pwdSecretName = process.env.KAFKA_PWD_SECRET_NAME;

    if (kvName && keySecretName && pwdSecretName) {
      const [username, password] = await Promise.all([
        getSecret(keySecretName),
        getSecret(pwdSecretName),
      ]);

      return { username, password };
    }

    return {
      username: process.env.KAFKA_USERNAME ?? null,
      password: process.env.KAFKA_PASSWORD ?? null,
    };
  }

  const credentials = await loadKafkaCredentials();

  const kafkaConfig: any = {
    clientId,
    brokers,
    ...(numberFromEnv("KAFKA_CONNECTION_TIMEOUT_MS") !== undefined
      ? { connectionTimeout: numberFromEnv("KAFKA_CONNECTION_TIMEOUT_MS") }
      : {}),
    ...(numberFromEnv("KAFKA_REQUEST_TIMEOUT_MS") !== undefined
      ? { requestTimeout: numberFromEnv("KAFKA_REQUEST_TIMEOUT_MS") }
      : {}),
    ...(Object.keys(retry).length ? { retry } : {}),
  };

  if (credentials.username && credentials.password) {
    kafkaConfig.ssl = true;
    kafkaConfig.sasl = {
      mechanism: "plain",
      username: credentials.username,
      password: credentials.password,
    };
  }

  const kafka = new Kafka(kafkaConfig);

  const kafkaProducer = kafka.producer();

  try {
    await kafkaProducer.connect();
  } catch (error) {
    logKafkaError("connect", error, {
      brokers,
      clientId,
    });

    throw error;
  }

  producer = kafkaProducer;

  return producer;
}

export async function publishRecords(
  records: SwapRecord[],
): Promise<RecordMetadata[]> {
  if (process.env.DISABLE_KAFKA === "true") {
    return [];
  }

  const topic = process.env.KAFKA_TOPIC;

  if (!topic) {
    throw new Error("Variável de ambiente KAFKA_TOPIC não configurada.");
  }

  if (!records.length) {
    return [];
  }

  const payloads = toKafkaSwapMessages(records);
  const messages = payloads.map((payload) => {
    return {
      key: `${payload.values.ticker}-${payload.values.diasUteis}`,
      value: JSON.stringify(payload),
    };
  });

  return sendMessages(topic, messages);
}

/**
 * Publica uma mensagem por agrupamento de curva (ex.: todos os vértices de
 * LFT em uma mensagem, todos os de NTN-B em outra).
 */
export async function publishGroupedRecords(
  records: SwapRecord[],
): Promise<RecordMetadata[]> {
  if (process.env.DISABLE_KAFKA === "true") {
    return [];
  }

  const topic = process.env.KAFKA_TOPIC;

  if (!topic) {
    throw new Error("Variável de ambiente KAFKA_TOPIC não configurada.");
  }

  if (!records.length) {
    return [];
  }

  const payloads = toKafkaGroupedSwapMessages(records);
  const messages = payloads.map((payload) => {
    return {
      key: `${payload.values.ticker}-${payload.values.refDate}`,
      value: JSON.stringify(payload),
    };
  });

  return sendMessages(topic, messages);
}

async function sendMessages(
  topic: string,
  messages: Array<{ key: string; value: string }>,
): Promise<RecordMetadata[]> {
  if (!messages.length) {
    return [];
  }

  logKafkaMessages(topic, messages);

  for (let attempt = 0; attempt < 2; attempt += 1) {
    const kafkaProducer = await getProducer();

    try {
      const metadata = await kafkaProducer.send({
        topic,
        messages,
        compression: CompressionTypes.GZIP,
      });

      logKafkaMetadata(metadata);

      return metadata;
    } catch (error) {
      logKafkaError("send", error, {
        topic,
        attempt: attempt + 1,
        messages: messages.map((message) => ({
          key: message.key,
          value: parseMessageValue(message.value),
        })),
      });

      if (attempt === 0 && isDisconnectedError(error)) {
        await resetProducer();
        continue;
      }

      throw error;
    }
  }

  return [];
}

export async function disconnectProducer(): Promise<void> {
  await resetProducer();
}
