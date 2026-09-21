import { describe, expect, it, vi, afterEach } from 'vitest';
import { FeederB3CurvaReferencia } from './b3-curva-referencia.js';
import type { EnviarMensagem } from '../kafka-publisher.js';

describe('FeederB3CurvaReferencia (Contract)', () => {
  const enviarMock: EnviarMensagem = vi.fn();
  
  afterEach(() => {
    vi.clearAllMocks();
  });

  const baseParams = {
    source: 'B3',
    dataset: 'B3_CURVA_PRE',
    faixa: 'ROTINA' as const,
    correlationId: 'c0eebc99-9c0b-4ef8-bb6d-6bb9bd380a55',
  };

  it('deve retornar PUBLISHED para dia útil com dados reais (2026-08-21)', async () => {
    const feeder = new FeederB3CurvaReferencia(enviarMock, {
      codigoCurva: 'PRE',
    });

    const resultado = await feeder.acquire({ ...baseParams, referenceDate: '2026-08-21' });

    expect(resultado.kind).toBe('PUBLISHED');
    expect(enviarMock).toHaveBeenCalled();
  }, 30_000);

  it('deve retornar NO_DATA para fim de semana real (2026-08-22)', async () => {
    const feeder = new FeederB3CurvaReferencia(enviarMock, {
      codigoCurva: 'PRE',
    });

    const resultado = await feeder.acquire({ ...baseParams, referenceDate: '2026-08-22' });

    expect(resultado.kind).toBe('NO_DATA');
    expect(enviarMock).not.toHaveBeenCalled();
  }, 30_000);
});
