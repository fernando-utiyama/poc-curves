const { existsSync } = require("fs");
const path = require("path");

const projectRoot = path.resolve(__dirname, "../../../..");
const compiledKafkaServicePath = path.join(projectRoot, "dist", "src", "b3", "producer", "sendKafkaB3.js");
const sourceKafkaServicePath = path.join(projectRoot, "src", "b3", "producer", "sendKafkaB3.ts");

function resolveKafkaServicePath() {
  if (existsSync(compiledKafkaServicePath)) {
    return compiledKafkaServicePath;
  }

  require("ts-node/register/transpile-only");
  return sourceKafkaServicePath;
}

function configureFailureScenario() {
  process.env.DISABLE_KAFKA = "false";
  process.env.KAFKA_BROKERS = process.env.KAFKA_BROKERS || "127.0.0.1:1";
  process.env.KAFKA_TOPIC =
    process.env.KAFKA_TOPIC ||
    "acts-dev-invgua-tcen-precif-curvas-b3-pubsub-negocio-json";
  process.env.KAFKA_CLIENT_ID = process.env.KAFKA_CLIENT_ID || "func-swap-b3-failure-test";
  process.env.KAFKA_CONNECTION_TIMEOUT_MS = process.env.KAFKA_CONNECTION_TIMEOUT_MS || "1000";
  process.env.KAFKA_REQUEST_TIMEOUT_MS = process.env.KAFKA_REQUEST_TIMEOUT_MS || "1000";
  process.env.KAFKA_RETRY_RETRIES = process.env.KAFKA_RETRY_RETRIES || "0";
  process.env.KAFKA_RETRY_INITIAL_MS = process.env.KAFKA_RETRY_INITIAL_MS || "100";
  process.env.LOG_KAFKA_MESSAGES = process.env.LOG_KAFKA_MESSAGES || "true";
  process.env.KAFKAJS_NO_PARTITIONER_WARNING = "1";
}

function buildRecord() {
  return {
    sequencial: "999999",
    tipoRegistro: "001",
    filler: "01",
    dataBase: "20260611",
    timestamp: "TFAIL",
    ticker: "LTN",
    descricao: "FALHA KAFKA",
    diasUteis: 10,
    diasCorridos: 12,
    taxaRaw: "+00000000123456",
    valor: 0.0123456,
    flagMercado: "M",
    prazoFinal: 12,
    fonteLayout: "simulate-kafka-publish-failure",
  };
}

async function main() {
  configureFailureScenario();

  const kafkaServicePath = resolveKafkaServicePath();
  const { publishRecords, disconnectProducer } = require(kafkaServicePath);

  console.log("Simulando falha de publicação Kafka com broker indisponível:");
  console.log(`KAFKA_BROKERS=${process.env.KAFKA_BROKERS}`);
  console.log(`KAFKA_TOPIC=${process.env.KAFKA_TOPIC}`);
  console.log(`KAFKA_CONNECTION_TIMEOUT_MS=${process.env.KAFKA_CONNECTION_TIMEOUT_MS}`);
  console.log(`KAFKA_RETRY_RETRIES=${process.env.KAFKA_RETRY_RETRIES}`);

  try {
    await publishRecords([buildRecord()]);
    throw new Error("A publicação Kafka não falhou como esperado.");
  } catch (error) {
    const message = error instanceof Error ? error.message : String(error);

    if (/não falhou como esperado/.test(message)) {
      throw error;
    }

    console.log("Falha Kafka simulada e capturada com sucesso.");
    console.log(`Erro capturado: ${message}`);
  } finally {
    await disconnectProducer();
  }
}

main().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
