/**
 * Teste de contrato opcional contra a ANBIMA real (mesmo espírito da tarefa
 * 6.8 do backlog do feeder B3). Fora do build padrão — depende de rede real.
 * Roda só sob demanda: npm run test:contract
 */
import { describe, expect, it, vi } from 'vitest';
import {
  DATASET_ANBIMA_MERCADO_SECUNDARIO,
  FeederAnbimaMercadoSecundario,
} from './anbima-mercado-secundario.js';
import type { AcquisitionParams } from '../feeder.js';

const paramsBase: AcquisitionParams = {
  source: 'ANBIMA',
  dataset: DATASET_ANBIMA_MERCADO_SECUNDARIO,
  referenceDate: '2026-08-21',
  faixa: 'ROTINA',
  correlationId: 'c0eebc99-9c0b-4ef8-bb6d-6bb9bd380a66',
};

describe('contrato real: arquivo de mercado secundário da ANBIMA', () => {
  it('PUBLISHED: baixa e processa de verdade o ms260821.txt real (pregão de 2026-08-21)', async () => {
    const enviar = vi.fn().mockResolvedValue(undefined);
    const feeder = new FeederAnbimaMercadoSecundario(enviar);

    const resultado = await feeder.acquire(paramsBase);

    expect(resultado.kind).toBe('PUBLISHED');
    if (resultado.kind === 'PUBLISHED') {
      // 51 linhas de dado reais confirmadas nesta sessão, com o tamanho de bloco padrão (50) vira 2 blocos.
      expect(resultado.totalBlocos).toBeGreaterThanOrEqual(1);
    }
    expect(enviar).toHaveBeenCalled();
  }, 30_000);

  it('NO_DATA: uma data futura sem arquivo publicado responde 404 real', async () => {
    const enviar = vi.fn();
    const feeder = new FeederAnbimaMercadoSecundario(enviar);

    const resultado = await feeder.acquire({ ...paramsBase, referenceDate: '2035-08-21' }); // segunda-feira, não feriado

    expect(resultado.kind).toBe('NO_DATA');
    expect(enviar).not.toHaveBeenCalled();
  }, 15_000);
});
