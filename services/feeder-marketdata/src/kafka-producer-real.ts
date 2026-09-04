import { Kafka, type Producer } from 'kafkajs';
import type { EnviarMensagem } from './kafka-publisher.js';

/** Produtor Kafka real (kafkajs), conectado uma vez e reaproveitado entre publicações. */
export interface ProdutorKafkaReal {
  readonly enviar: EnviarMensagem;
  desconectar(): Promise<void>;
}

/**
 * Cria e conecta um produtor Kafka real, expondo `enviar` na mesma forma que
 * `publicarBloco` (kafka-publisher.ts) espera receber por injeção — o núcleo
 * do feeder nunca importa `kafkajs` diretamente, só esta função de borda.
 */
export async function criarProdutorKafkaReal(bootstrapServers: string): Promise<ProdutorKafkaReal> {
  const kafka = new Kafka({
    clientId: 'feeder-marketdata',
    brokers: bootstrapServers.split(',').map((b) => b.trim()),
  });
  const producer: Producer = kafka.producer({ allowAutoTopicCreation: false });
  await producer.connect();

  const enviar: EnviarMensagem = async ({ topico, chave, valor }) => {
    await producer.send({
      topic: topico,
      messages: [{ key: chave, value: valor }],
    });
  };

  return {
    enviar,
    desconectar: () => producer.disconnect(),
  };
}
