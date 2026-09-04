import { describe, expect, it, vi } from 'vitest';
import { FeederBcbSerieTemporal, DATASET_BCB_CDI, DATASET_BCB_SELIC } from './bcb-serie-temporal.js';
import * as calendario from '../calendario.js';

describe('FeederBcbSerieTemporal', () => {
  const paramsPadrao = {
    source: 'BCB',
    dataset: DATASET_BCB_CDI,
    referenceDate: '2026-08-20',
    faixa: 'ROTINA' as const,
    correlationId: 'c0eebc99-9c0b-4ef8-bb6d-6bb9bd380a55'
  };

  it('a) PUBLISHED: sucesso com 1 elemento no array', async () => {
    const enviar = vi.fn().mockResolvedValue(undefined);
    vi.spyOn(calendario, 'ehDiaDePregao').mockReturnValue(true);

    const corpo = '[{"data":"20/08/2026","valor":"13.90"}]';
    const fetchImpl = vi.fn().mockResolvedValue(new Response(corpo, { status: 200 }));
    
    const feeder = new FeederBcbSerieTemporal(enviar, 4389, { fetchImpl });
    const resultado = await feeder.acquire(paramsPadrao);

    expect(resultado.kind).toBe('PUBLISHED');
    if (resultado.kind === 'PUBLISHED') {
      expect(resultado.totalBlocos).toBe(1);
    }
    
    expect(enviar).toHaveBeenCalledTimes(1);
    const chamada = enviar.mock.calls[0]![0];
    const valorParseado = JSON.parse(chamada.valor);
    expect(valorParseado.payload.records[0].raw).toBe('{"data":"20/08/2026","valor":"13.90"}');
  });

  it('b) NO_DATA com status HTTP 404 real', async () => {
    const enviar = vi.fn().mockResolvedValue(undefined);
    vi.spyOn(calendario, 'ehDiaDePregao').mockReturnValue(true);

    const corpo = '{"erro":{"statusCode":404,"detail":"br.gov.bcb.pec.sgs.comum.excecoes.SGSNegocioException: Value(s) not found"}}';
    const fetchImpl = vi.fn().mockResolvedValue(new Response(corpo, { status: 404 }));
    
    const feeder = new FeederBcbSerieTemporal(enviar, 4389, { fetchImpl });
    const resultado = await feeder.acquire(paramsPadrao);

    expect(resultado.kind).toBe('NO_DATA');
    expect(enviar).not.toHaveBeenCalled();
  });

  it('c) NO_DATA com status HTTP 200 "mentiroso"', async () => {
    const enviar = vi.fn().mockResolvedValue(undefined);
    vi.spyOn(calendario, 'ehDiaDePregao').mockReturnValue(true);

    const corpo = '{"erro":{"statusCode":404,"detail":"br.gov.bcb.pec.sgs.comum.excecoes.SGSNegocioException: Value(s) not found"}}';
    const fetchImpl = vi.fn().mockResolvedValue(new Response(corpo, { status: 200 }));
    
    const feeder = new FeederBcbSerieTemporal(enviar, 4389, { fetchImpl });
    const resultado = await feeder.acquire(paramsPadrao);

    expect(resultado.kind).toBe('NO_DATA');
    expect(enviar).not.toHaveBeenCalled();
  });

  it('d) NO_DATA com array vazio', async () => {
    const enviar = vi.fn().mockResolvedValue(undefined);
    vi.spyOn(calendario, 'ehDiaDePregao').mockReturnValue(true);

    const corpo = '[]';
    const fetchImpl = vi.fn().mockResolvedValue(new Response(corpo, { status: 200 }));
    
    const feeder = new FeederBcbSerieTemporal(enviar, 4389, { fetchImpl });
    const resultado = await feeder.acquire(paramsPadrao);

    expect(resultado.kind).toBe('NO_DATA');
  });

  it('e) FAILED com statusCode >= 500 embutido', async () => {
    const enviar = vi.fn().mockResolvedValue(undefined);
    vi.spyOn(calendario, 'ehDiaDePregao').mockReturnValue(true);

    const corpo = '{"erro":{"statusCode":503,"detail":"indisponível"}}';
    const fetchImpl = vi.fn().mockResolvedValue(new Response(corpo, { status: 200 }));
    
    const feeder = new FeederBcbSerieTemporal(enviar, 4389, { fetchImpl });
    const resultado = await feeder.acquire(paramsPadrao);

    expect(resultado.kind).toBe('FAILED');
    expect(enviar).not.toHaveBeenCalled();
  });

  it('f) FAILED com JSON inválido', async () => {
    const enviar = vi.fn().mockResolvedValue(undefined);
    vi.spyOn(calendario, 'ehDiaDePregao').mockReturnValue(true);

    const corpo = 'não é json';
    const fetchImpl = vi.fn().mockResolvedValue(new Response(corpo, { status: 200 }));
    
    const feeder = new FeederBcbSerieTemporal(enviar, 4389, { fetchImpl });
    const resultado = await feeder.acquire(paramsPadrao);

    expect(resultado.kind).toBe('FAILED');
  });

  it('g) FAILED por falha de transporte', async () => {
    const enviar = vi.fn().mockResolvedValue(undefined);
    vi.spyOn(calendario, 'ehDiaDePregao').mockReturnValue(true);

    const fetchImpl = vi.fn().mockRejectedValue(new Error('ECONNRESET'));

    const feeder = new FeederBcbSerieTemporal(enviar, 4389, {
      fetchImpl,
      httpConfig: { timeoutMs: 1000, maxRetries: 1, baseBackoffMs: 1 },
    });
    const resultado = await feeder.acquire(paramsPadrao);

    expect(resultado.kind).toBe('FAILED');
  });

  it('h) Não é dia de pregão', async () => {
    const enviar = vi.fn().mockResolvedValue(undefined);
    vi.spyOn(calendario, 'ehDiaDePregao').mockReturnValue(false);

    const fetchImpl = vi.fn();
    
    const feeder = new FeederBcbSerieTemporal(enviar, 4389, { fetchImpl });
    const resultado = await feeder.acquire({ ...paramsPadrao, referenceDate: '2026-08-22' });

    expect(resultado.kind).toBe('NO_DATA');
    expect(fetchImpl).not.toHaveBeenCalled();
  });

  it('i) Dois datasets, mesma classe, urls diferentes', async () => {
    const enviar = vi.fn().mockResolvedValue(undefined);
    vi.spyOn(calendario, 'ehDiaDePregao').mockReturnValue(true);

    const corpo = '[{"data":"20/08/2026","valor":"13.90"}]';
    const fetchImpl = vi.fn().mockImplementation(() => Promise.resolve(new Response(corpo, { status: 200 })));

    const feederCdi = new FeederBcbSerieTemporal(enviar, 4389, { fetchImpl });
    await feederCdi.acquire({ ...paramsPadrao, dataset: DATASET_BCB_CDI });
    
    expect(fetchImpl).toHaveBeenCalledTimes(1);
    expect(fetchImpl.mock.calls[0]![0]).toContain('sgs.4389');

    fetchImpl.mockClear();

    const feederSelic = new FeederBcbSerieTemporal(enviar, 1178, { fetchImpl });
    await feederSelic.acquire({ ...paramsPadrao, dataset: DATASET_BCB_SELIC });
    
    expect(fetchImpl).toHaveBeenCalledTimes(1);
    expect(fetchImpl.mock.calls[0]![0]).toContain('sgs.1178');
  });
});
