import { afterEach, describe, expect, it, vi } from 'vitest';
import { FeederB3CurvaReferencia } from './b3-curva-referencia.js';
import type { EnviarMensagem } from '../kafka-publisher.js';

describe('FeederB3CurvaReferencia', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
    vi.clearAllMocks();
  });

  const enviarMock: EnviarMensagem = vi.fn();
  const baseParams = {
    source: 'B3',
    dataset: 'B3_CURVA_PRE',
    referenceDate: '2026-08-21',
    faixa: 'ROTINA' as const,
    correlationId: 'c0eebc99-9c0b-4ef8-bb6d-6bb9bd380a55',
  };

  const csvExemplo = 'Descrição da Taxa;Dias Úteis;Dias Corridos;Preço/Taxa\r\nDI x pré;1;3;13,90\r\nDI x pré;4;6;13,90\r\n';
  const corpoBase64 = Buffer.from(csvExemplo, 'latin1').toString('base64');

  it('deve retornar PUBLISHED e publicar bloco corretamente', async () => {
    const fetchFake = vi.fn().mockResolvedValue(new Response(corpoBase64, { status: 200 }));
    const feeder = new FeederB3CurvaReferencia(enviarMock, {
      codigoCurva: 'PRE',
      fetchImpl: fetchFake,
    });

    const resultado = await feeder.acquire(baseParams);

    expect(resultado.kind).toBe('PUBLISHED');
    expect(enviarMock).toHaveBeenCalledTimes(1);

    const callArgs = vi.mocked(enviarMock).mock.calls[0]![0];
    const envelope = JSON.parse(callArgs.valor);

    expect(envelope.payloadKind).toBe('READY_CURVE');
    expect(envelope.payload.records[0].raw).toContain('DI x pré');
  });

  it('deve retornar NO_DATA quando corpo for vazio (sinal real confirmado)', async () => {
    const fetchFake = vi.fn().mockResolvedValue(new Response('', { status: 200 }));
    const feeder = new FeederB3CurvaReferencia(enviarMock, {
      codigoCurva: 'PRE',
      fetchImpl: fetchFake,
    });

    const resultado = await feeder.acquire(baseParams);

    expect(resultado.kind).toBe('NO_DATA');
    expect(enviarMock).not.toHaveBeenCalled();
  });

  it('deve retornar NO_DATA para 404 real', async () => {
    const fetchFake = vi.fn().mockResolvedValue(new Response('', { status: 404 }));
    const feeder = new FeederB3CurvaReferencia(enviarMock, {
      codigoCurva: 'PRE',
      fetchImpl: fetchFake,
    });

    const resultado = await feeder.acquire(baseParams);

    expect(resultado.kind).toBe('NO_DATA');
  });

  it('deve retornar FAILED para 5xx real', async () => {
    const fetchFake = vi.fn().mockResolvedValue(new Response('erro interno', { status: 503 }));
    const feeder = new FeederB3CurvaReferencia(enviarMock, {
      codigoCurva: 'PRE',
      fetchImpl: fetchFake,
    });

    const resultado = await feeder.acquire(baseParams);

    expect(resultado.kind).toBe('FAILED');
  });

  it('deve retornar FAILED por falha de transporte', async () => {
    const fetchFake = vi.fn().mockRejectedValue(new Error('ECONNRESET'));
    const feeder = new FeederB3CurvaReferencia(enviarMock, {
      codigoCurva: 'PRE',
      fetchImpl: fetchFake,
      httpConfig: { timeoutMs: 1000, maxRetries: 1, baseBackoffMs: 1 },
    });

    const resultado = await feeder.acquire(baseParams);

    expect(resultado.kind).toBe('FAILED');
  });

  it('deve retornar NO_DATA se não é dia de pregão', async () => {
    const fetchFake = vi.fn();
    const feeder = new FeederB3CurvaReferencia(enviarMock, {
      codigoCurva: 'PRE',
      fetchImpl: fetchFake,
    });

    const resultado = await feeder.acquire({ ...baseParams, referenceDate: '2026-08-22' });

    expect(resultado.kind).toBe('NO_DATA');
    expect(fetchFake).not.toHaveBeenCalled();
  });

  it('deve enviar headers reais quando nenhum fetchImpl é injetado', async () => {
    const fetchFake = vi.fn().mockResolvedValue(new Response(corpoBase64, { status: 200 }));
    vi.stubGlobal('fetch', fetchFake);

    const feeder = new FeederB3CurvaReferencia(enviarMock, {
      codigoCurva: 'PRE',
    });

    await feeder.acquire(baseParams);

    expect(fetchFake).toHaveBeenCalledTimes(1);
    const init = fetchFake.mock.calls[0]![1];
    expect(init.headers).toHaveProperty('User-Agent');
    expect(init.headers['User-Agent']).toContain('Mozilla/5.0');
  });

  it('deve montar a URL baseada no codigoCurva fornecido', async () => {
    const fetchFake = vi.fn().mockResolvedValue(new Response(corpoBase64, { status: 200 }));
    const feeder = new FeederB3CurvaReferencia(enviarMock, {
      codigoCurva: 'PRE',
      fetchImpl: fetchFake,
    });

    await feeder.acquire(baseParams);

    const urlChamada = fetchFake.mock.calls[0]![0];
    const base64Esperado = Buffer.from(
      JSON.stringify({ language: 'pt-br', date: '2026-08-21', id: 'PRE' })
    ).toString('base64');

    expect(urlChamada).toContain(base64Esperado);
  });
});
