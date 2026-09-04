import { describe, expect, it, vi } from 'vitest';
import type { Feeder } from './feeder.js';
import { DatasetNaoSuportadoError, RegistroFeeders } from './registro-feeders.js';

describe('RegistroFeeders', () => {
  it('retorna a mesma instância registrada ao resolver dataset', () => {
    const registro = new RegistroFeeders();
    const feederFalso: Feeder = { acquire: vi.fn() };

    registro.registrar('PR_DI1', feederFalso);

    expect(registro.resolver('PR_DI1')).toBe(feederFalso);
  });

  it('lança DatasetNaoSuportadoError com o nome do dataset ao resolver dataset inexistente', () => {
    const registro = new RegistroFeeders();

    expect(() => registro.resolver('DATASET_INEXISTENTE')).toThrow(DatasetNaoSuportadoError);
    expect(() => registro.resolver('DATASET_INEXISTENTE')).toThrow('DATASET_INEXISTENTE');
  });

  it('lança erro ao registrar o mesmo nome de dataset duas vezes', () => {
    const registro = new RegistroFeeders();
    const feederFalso: Feeder = { acquire: vi.fn() };
    const outroFeederFalso: Feeder = { acquire: vi.fn() };

    registro.registrar('PR_DI1', feederFalso);

    expect(() => registro.registrar('PR_DI1', outroFeederFalso)).toThrow(
      'dataset já registrado: PR_DI1',
    );
  });

  it('datasetsRegistrados retorna array vazio quando vazio e lista completa com múltiplos registros', () => {
    const registro = new RegistroFeeders();
    const feederFalso: Feeder = { acquire: vi.fn() };

    expect(registro.datasetsRegistrados()).toEqual([]);

    registro.registrar('PR_DI1', feederFalso);
    registro.registrar('BVBG.086', feederFalso);

    const datasets = registro.datasetsRegistrados();
    expect(datasets).toHaveLength(2);
    expect(datasets).toEqual(expect.arrayContaining(['PR_DI1', 'BVBG.086']));
  });

  it('lança erro que é instância tanto de DatasetNaoSuportadoError quanto de Error', () => {
    const registro = new RegistroFeeders();

    let erroCapturado: unknown;
    try {
      registro.resolver('DATASET_INEXISTENTE');
    } catch (err) {
      erroCapturado = err;
    }

    expect(erroCapturado).toBeInstanceOf(DatasetNaoSuportadoError);
    expect(erroCapturado).toBeInstanceOf(Error);
  });
});
