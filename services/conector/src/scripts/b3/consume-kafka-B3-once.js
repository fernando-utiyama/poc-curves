const { Kafka } = require("kafkajs");

const brokers = (process.env.KAFKA_BROKERS || "localhost:29092")
  .split(",")
  .map((item) => item.trim())
  .filter(Boolean);

const topic =
  process.env.KAFKA_TOPIC ||
  "acts-dev-invgua-tcen-precif-curvas-b3-pubsub-negocio-json";
const maxMessages = Number(process.env.KAFKA_CONSUME_MAX_MESSAGES || "2");
const timeoutMs = Number(process.env.KAFKA_CONSUME_TIMEOUT_MS || "60000");

async function main() {
  const kafka = new Kafka({
    clientId: "func-swap-b3-debug-consumer",
    brokers,
  });

  const consumer = kafka.consumer({
    groupId: `func-swap-b3-debug-${Date.now()}`,
  });

  const messages = [];

  await consumer.connect();
  await consumer.subscribe({ topic, fromBeginning: false });

  console.log(
    `Aguardando ate ${maxMessages} mensagem(ns) em ${topic} via ${brokers.join(", ")}...`,
  );

  await new Promise((resolve, reject) => {
    const timer = setTimeout(resolve, timeoutMs);

    consumer
      .run({
        eachMessage: async ({ partition, message }) => {
          const rawValue = message.value?.toString() || "";
          let value = rawValue;

          try {
            value = JSON.parse(rawValue);
          } catch {
            // Keep raw value when the Kafka message is not JSON.
          }

          messages.push({
            topic,
            partition,
            offset: message.offset,
            key: message.key?.toString() || null,
            value,
          });

          if (messages.length >= maxMessages) {
            clearTimeout(timer);
            resolve();
          }
        },
      })
      .catch((error) => {
        clearTimeout(timer);
        reject(error);
      });
  });

  await consumer.disconnect();

  if (!messages.length) {
    console.log("Nenhuma mensagem consumida dentro do timeout.");
    process.exitCode = 1;
    return;
  }

  for (const entry of messages) {
    console.log(JSON.stringify(entry, null, 2));
  }
}

main().catch(async (error) => {
  console.error(error);
  process.exitCode = 1;
});
