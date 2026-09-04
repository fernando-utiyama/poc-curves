import { describe, expect, it, vi } from 'vitest';
import type { ParametrosCriarEnvelope } from './envelope.js';
import { publicarBloco } from './kafka-publisher.js';

describe('publicarBloco', () => {
  const base: ParametrosCriarEnvelope = {
    eventId: 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11',
    correlationId: 'b0eebc99-9c0b-4ef8-bb6d-6bb9bd380a22',
    source: 'B3',
    dataset: 'PR_DI1',
    referenceDate: '2026-08-21',
    producedAt: '2026-08-21T18:00:00Z',
    schemaVersion: '1.0',
    payloadKind: 'INDIVIDUAL_QUOTES',
    loteId: 'lote-abc-1',
    sequencia: 1,
    totalBlocos: 1,
    payload: {},
  };

  it('chama enviarFake exatamente 1 vez com topico e chave corretos para faixa ROTINA', async () => {
    const enviarFake = vi.fn().mockResolvedValue(undefined);

    await publicarBloco({ envelope: base, faixa: 'ROTINA' }, enviarFake);

    expect(enviarFake).toHaveBeenCalledTimes(1);
    expect(enviarFake).toHaveBeenCalledWith({
      topico: 'marketdata.rotina.v1',
      chave: 'B3|PR_DI1|2026-08-21',
      valor: expect.any(String),
    });
  });

  it('passa valor serializado em JSON com dataset e loteId corretos', async () => {
    const enviarFake = vi.fn().mockResolvedValue(undefined);

    await publicarBloco({ envelope: base, faixa: 'ROTINA' }, enviarFake);

    expect(enviarFake).toHaveBeenCalledTimes(1);
    const chamada = enviarFake.mock.calls[0]?.[0];
    expect(chamada).toBeDefined();
    const valorDesserializado = JSON.parse(chamada.valor);
    expect(valorDesserializado.dataset).toBe('PR_DI1');
    expect(valorDesserializado.loteId).toBe('lote-abc-1');
  });

  it('resolve topico correto para faixas PRIORITARIA e MASSA', async () => {
    const enviarFakePrioritaria = vi.fn().mockResolvedValue(undefined);
    await publicarBloco({ envelope: base, faixa: 'PRIORITARIA' }, enviarFakePrioritaria);
    expect(enviarFakePrioritaria).toHaveBeenCalledWith(
      expect.objectContaining({ topico: 'marketdata.prioritaria.v1' }),
    );

    const enviarFakeMassa = vi.fn().mockResolvedValue(undefined);
    await publicarBloco({ envelope: base, faixa: 'MASSA' }, enviarFakeMassa);
    expect(enviarFakeMassa).toHaveBeenCalledWith(
      expect.objectContaining({ topico: 'marketdata.massa.v1' }),
    );
  });

  it('retorna envelope e log com o mesmo correlationId propagado', async () => {
    const enviarFake = vi.fn().mockResolvedValue(undefined);

    const resultado = await publicarBloco({ envelope: base, faixa: 'ROTINA' }, enviarFake);

    expect(resultado.envelope.correlationId).toBe(base.correlationId);
    expect(resultado.log.correlationId).toBe(base.correlationId);
  });

  it('chama registrarLog injetado exatamente 1 vez com mensagem e topico esperados', async () => {
    const enviarFake = vi.fn().mockResolvedValue(undefined);
    const registrarLogFake = vi.fn();

    await publicarBloco({ envelope: base, faixa: 'ROTINA' }, enviarFake, registrarLogFake);

    expect(registrarLogFake).toHaveBeenCalledTimes(1);
    expect(registrarLogFake).toHaveBeenCalledWith({
      nivel: 'info',
      mensagem: 'bloco publicado',
      correlationId: base.correlationId,
      dataset: 'PR_DI1',
      referenceDate: '2026-08-21',
      topico: 'marketdata.rotina.v1',
      loteId: 'lote-abc-1',
      sequencia: 1,
      totalBlocos: 1,
    });
  });

  it('lança erro e não chama enviarFake quando os parâmetros do envelope são inválidos', async () => {
    const enviarFake = vi.fn().mockResolvedValue(undefined);
    const envelopeInvalido: ParametrosCriarEnvelope = { ...base, eventId: '' };

    await expect(
      publicarBloco({ envelope: envelopeInvalido, faixa: 'ROTINA' }, enviarFake),
    ).rejects.toThrow('eventId não pode ser vazio');

    expect(enviarFake).not.toHaveBeenCalled();
  });
});
