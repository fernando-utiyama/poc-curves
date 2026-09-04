import { describe, expect, it, vi } from 'vitest';
import type { ParametrosCriarEnvelope } from './envelope.js';
import { publicarBloco } from './kafka-publisher.js';

describe('Publicação de lote com falha parcial', () => {
  it('propaga o erro no bloco que falhar e permite identificar o lote incompleto e a sequência de falha', async () => {
    const loteId = 'lote-falha-parcial-1';
    const totalBlocos = 3;

    const baseEnvelope: Omit<ParametrosCriarEnvelope, 'eventId' | 'sequencia'> = {
      correlationId: 'b0eebc99-9c0b-4ef8-bb6d-6bb9bd380a22',
      source: 'B3',
      dataset: 'PR_DI1',
      referenceDate: '2026-08-21',
      producedAt: '2026-08-21T18:00:00Z',
      schemaVersion: '1.0',
      payloadKind: 'INDIVIDUAL_QUOTES',
      loteId,
      totalBlocos,
      payload: {},
    };

    const bloco1Envelope: ParametrosCriarEnvelope = {
      ...baseEnvelope,
      eventId: 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11',
      sequencia: 1,
    };

    const bloco2Envelope: ParametrosCriarEnvelope = {
      ...baseEnvelope,
      eventId: 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a12',
      sequencia: 2,
    };

    const erroSimulado = new Error('falha de transporte simulada');
    const enviarFake = vi.fn().mockResolvedValueOnce(undefined).mockRejectedValueOnce(erroSimulado);

    // 1. Bloco 1 publica com sucesso
    const resultadoBloco1 = await publicarBloco(
      { envelope: bloco1Envelope, faixa: 'ROTINA' },
      enviarFake,
    );

    expect(resultadoBloco1.envelope.loteId).toBe(loteId);
    expect(resultadoBloco1.envelope.sequencia).toBe(1);
    expect(resultadoBloco1.envelope.totalBlocos).toBe(3);

    // 2. Bloco 2 falha e propaga o erro exato
    await expect(
      publicarBloco({ envelope: bloco2Envelope, faixa: 'ROTINA' }, enviarFake),
    ).rejects.toThrow('falha de transporte simulada');

    // 3. O bloco 3 não é tentado — enviarFake foi chamado exatamente 2 vezes
    expect(enviarFake).toHaveBeenCalledTimes(2);
  });
});
