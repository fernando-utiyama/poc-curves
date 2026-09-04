import { HttpRequest, InvocationContext } from '@azure/functions';
import { describe, expect, it, vi } from 'vitest';
import { criarAcquireHandler } from './azure-function-handler.js';
import type { EnviarMensagem } from './kafka-publisher.js';
import type { ProdutorKafkaReal } from './kafka-producer-real.js';

function requisicao(corpo: unknown): HttpRequest {
  return new HttpRequest({
    method: 'POST',
    url: 'http://localhost/api/acquire',
    body: { string: JSON.stringify(corpo) },
  });
}

function contexto(): InvocationContext {
  return new InvocationContext({ functionName: 'acquire', invocationId: 'teste-1' });
}

function fabricaProdutorFake(enviar: EnviarMensagem = vi.fn().mockResolvedValue(undefined)) {
  const desconectar = vi.fn().mockResolvedValue(undefined);
  const fabrica = vi.fn().mockResolvedValue({ enviar, desconectar } satisfies ProdutorKafkaReal);
  return { fabrica, desconectar };
}

describe('azure function handler acquireHandler', () => {
  it('400: corpo não é JSON válido — nem tenta conectar ao Kafka', async () => {
    const { fabrica } = fabricaProdutorFake();
    const handler = criarAcquireHandler(fabrica);

    const requisicaoInvalida = new HttpRequest({
      method: 'POST',
      url: 'http://localhost/api/acquire',
      body: { string: '{ isto nao eh json' },
    });

    const resposta = await handler(requisicaoInvalida, contexto());

    expect(resposta.status).toBe(400);
    expect(fabrica).not.toHaveBeenCalled();
  });

  it('400: campos obrigatórios ausentes, listados na resposta — nem tenta conectar ao Kafka', async () => {
    const { fabrica } = fabricaProdutorFake();
    const handler = criarAcquireHandler(fabrica);

    const resposta = await handler(requisicao({ dataset: 'BVBG.086' }), contexto());

    expect(resposta.status).toBe(400);
    expect(JSON.stringify(resposta.jsonBody)).toContain('referenceDate');
    expect(JSON.stringify(resposta.jsonBody)).toContain('faixa');
    expect(JSON.stringify(resposta.jsonBody)).toContain('kafkaBootstrapServers');
    expect(fabrica).not.toHaveBeenCalled();
  });

  it('400: dataset não suportado, nomeado — mas só depois de conectar (a validação de dataset é dentro do registro)', async () => {
    const { fabrica, desconectar } = fabricaProdutorFake();
    const handler = criarAcquireHandler(fabrica);

    const resposta = await handler(
      requisicao({
        dataset: 'DATASET_INEXISTENTE',
        referenceDate: '2026-08-21',
        faixa: 'ROTINA',
        kafkaBootstrapServers: 'localhost:9092',
      }),
      contexto(),
    );

    expect(resposta.status).toBe(400);
    expect(JSON.stringify(resposta.jsonBody)).toContain('DATASET_INEXISTENTE');
    expect(desconectar).toHaveBeenCalledTimes(1);
  });

  it('200: NO_DATA (dia sem pregão) — não chama enviar, desconecta o produtor ao final', async () => {
    const enviar = vi.fn();
    const { fabrica, desconectar } = fabricaProdutorFake(enviar);
    const handler = criarAcquireHandler(fabrica);

    // Domingo — nunca é dia de pregão, então nem tenta a rede real da B3.
    const resposta = await handler(
      requisicao({
        dataset: 'BVBG.086',
        referenceDate: '2026-08-23',
        faixa: 'ROTINA',
        kafkaBootstrapServers: 'localhost:9092',
      }),
      contexto(),
    );

    expect(resposta.status).toBe(200);
    const corpo = resposta.jsonBody as { correlationId: string; resultado: { kind: string } };
    expect(corpo.resultado.kind).toBe('NO_DATA');
    expect(enviar).not.toHaveBeenCalled();
    expect(desconectar).toHaveBeenCalledTimes(1);
  });

  it('gera correlationId quando não informado', async () => {
    const { fabrica } = fabricaProdutorFake();
    const handler = criarAcquireHandler(fabrica);

    const resposta = await handler(
      requisicao({
        dataset: 'BVBG.086',
        referenceDate: '2026-08-23',
        faixa: 'ROTINA',
        kafkaBootstrapServers: 'localhost:9092',
      }),
      contexto(),
    );

    const corpo = resposta.jsonBody as { correlationId: string };
    expect(corpo.correlationId).toBeDefined();
    expect(corpo.correlationId.length).toBeGreaterThan(0);
  });

  it('propaga o correlationId informado no corpo da requisição', async () => {
    const { fabrica } = fabricaProdutorFake();
    const handler = criarAcquireHandler(fabrica);

    const resposta = await handler(
      requisicao({
        dataset: 'BVBG.086',
        referenceDate: '2026-08-23',
        faixa: 'ROTINA',
        kafkaBootstrapServers: 'localhost:9092',
        correlationId: 'c0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11',
      }),
      contexto(),
    );

    const corpo = resposta.jsonBody as { correlationId: string };
    expect(corpo.correlationId).toBe('c0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11');
  });
});
