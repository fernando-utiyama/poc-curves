import { describe, expect, it, vi } from 'vitest';
import { FeederBcbSerieTemporal, DATASET_BCB_CDI, DATASET_BCB_SELIC } from './bcb-serie-temporal.js';
import * as calendario from '../calendario.js';

describe('FeederBcbSerieTemporal Contract', () => {
  const paramsBase = {
    source: 'BCB',
    faixa: 'ROTINA' as const,
    correlationId: 'c0eebc99-9c0b-4ef8-bb6d-6bb9bd380a55'
  };

  it('a) PUBLISHED real, CDI', async () => {
    const enviar = vi.fn().mockResolvedValue(undefined);
    vi.spyOn(calendario, 'ehDiaDePregao').mockReturnValue(true);
    
    const feeder = new FeederBcbSerieTemporal(enviar, 4389);
    const resultado = await feeder.acquire({ ...paramsBase, dataset: DATASET_BCB_CDI, referenceDate: '2026-08-20' });

    expect(resultado.kind).toBe('PUBLISHED');
    expect(enviar).toHaveBeenCalledTimes(1);
  }, 30_000);

  it('b) PUBLISHED real, SELIC', async () => {
    const enviar = vi.fn().mockResolvedValue(undefined);
    vi.spyOn(calendario, 'ehDiaDePregao').mockReturnValue(true);
    
    const feeder = new FeederBcbSerieTemporal(enviar, 1178);
    const resultado = await feeder.acquire({ ...paramsBase, dataset: DATASET_BCB_SELIC, referenceDate: '2026-08-20' });

    expect(resultado.kind).toBe('PUBLISHED');
    expect(enviar).toHaveBeenCalledTimes(1);
  }, 30_000);

  it('c) NO_DATA real, fim de semana', async () => {
    const enviar = vi.fn().mockResolvedValue(undefined);
    vi.spyOn(calendario, 'ehDiaDePregao').mockReturnValue(true); // force true to hit API
    
    const feeder = new FeederBcbSerieTemporal(enviar, 4389);
    const resultado = await feeder.acquire({ ...paramsBase, dataset: DATASET_BCB_CDI, referenceDate: '2026-08-22' });

    expect(resultado.kind).toBe('NO_DATA');
    expect(enviar).not.toHaveBeenCalled();
  }, 30_000);
});
