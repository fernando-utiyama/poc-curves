/**
 * Teste de contrato opcional contra a B3 real (tarefa 6.8 do backlog).
 * Fora do build padrão de propósito — depende de rede real e de um endpoint
 * de terceiros fora do nosso controle. Roda só sob demanda:
 *
 *   npm run test:contract
 *
 * (config dedicada `vitest.contract.config.ts`, incluindo apenas
 * `src/**\/*.contract.ts` — nunca coletado por `npm test`, que só olha
 * `*.test.ts`, então a suíte padrão continua passando sem acesso à internet,
 * tarefa 6.10).
 */
import { describe, expect, it, vi } from 'vitest';
import { FeederB3ArquivoPesquisaPregao } from './b3-arquivo-pesquisa-pregao.js';
import type { AcquisitionParams } from '../feeder.js';

const paramsBase: AcquisitionParams = {
  source: 'B3',
  dataset: 'BVBG.086',
  referenceDate: '2026-08-21',
  faixa: 'ROTINA',
  correlationId: 'c0eebc99-9c0b-4ef8-bb6d-6bb9bd380a99',
};

describe('contrato real: endpoint pesquisapregao da B3', () => {
  it('PUBLISHED: baixa e processa de verdade o PR260821.zip real (pregão de 2026-08-21)', async () => {
    const enviar = vi.fn().mockResolvedValue(undefined);
    const feeder = new FeederB3ArquivoPesquisaPregao(enviar, { prefixoArquivo: 'PR' });

    const resultado = await feeder.acquire(paramsBase);

    expect(resultado.kind).toBe('PUBLISHED');
    if (resultado.kind === 'PUBLISHED') {
      // Pior caso medido nesta sessão (tarefa 6.7.7): ~76.515 elementos reais.
      expect(resultado.totalBlocos).toBeGreaterThan(400);
    }
    expect(enviar).toHaveBeenCalled();
  }, 90_000);

  it('PUBLISHED: baixa e processa de verdade o IN260821.zip real (~800MB, o dataset que originalmente estourava o limite de string do V8)', async () => {
    const enviar = vi.fn().mockResolvedValue(undefined);
    const feeder = new FeederB3ArquivoPesquisaPregao(enviar, { prefixoArquivo: 'IN' });

    const resultado = await feeder.acquire({ ...paramsBase, dataset: 'BVBG.028' });

    expect(resultado.kind).toBe('PUBLISHED');
    if (resultado.kind === 'PUBLISHED') {
      // Pior caso medido nesta sessão (tarefa 6.7.7): ~223.700 elementos reais.
      expect(resultado.totalBlocos).toBeGreaterThan(1000);
    }
    expect(enviar).toHaveBeenCalled();
  }, 120_000);

  it('NO_DATA: uma data futura ainda sem arquivo publicado responde com ZIP vazio, não erro — confirmado real que o endpoint sempre devolve HTTP 200 (verificado nesta sessão: B3 mantém arquivo publicado até para 2020-01-02, então uma data passada não serve como "nunca existirá" — só uma data futura garante isso)', async () => {
    const enviar = vi.fn();
    const feeder = new FeederB3ArquivoPesquisaPregao(enviar, { prefixoArquivo: 'PR' });

    const resultado = await feeder.acquire({ ...paramsBase, referenceDate: '2035-08-21' }); // Monday, not a holiday

    expect(resultado.kind).toBe('NO_DATA');
    expect(enviar).not.toHaveBeenCalled();
  }, 30_000);
});
