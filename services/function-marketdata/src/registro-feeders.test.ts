import { describe, expect, it, vi } from 'vitest';
import type { Feeder } from './feeder.js';
import { DatasetNaoSuportadoError, RegistroFeeders } from './registro-feeders.js';

describe('RegistroFeeders', () => {
  it('retorna a mesma instância registrada ao resolver dataset', () => {
    const registro = new RegistroFeeders();
    const feederFalso: Feeder = { acquire: vi.fn() };

    registro.registrar('B3_TAXA_SWAP_DCL', feederFalso);

    expect(registro.resolver('B3_TAXA_SWAP_DCL')).toBe(feederFalso);
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

    registro.registrar('B3_TAXA_SWAP_DCL', feederFalso);

    expect(() => registro.registrar('B3_TAXA_SWAP_DCL', outroFeederFalso)).toThrow(
      'dataset já registrado: B3_TAXA_SWAP_DCL',
    );
  });

  it('datasetsRegistrados retorna array vazio quando vazio e lista completa com múltiplos registros', () => {
    const registro = new RegistroFeeders();
    const feederFalso: Feeder = { acquire: vi.fn() };

    expect(registro.datasetsRegistrados()).toEqual([]);

    registro.registrar('B3_TAXA_SWAP_DCL', feederFalso);
    registro.registrar('B3_TAXA_SWAP_PTX', feederFalso);

    const datasets = registro.datasetsRegistrados();
    expect(datasets).toHaveLength(2);
    expect(datasets).toEqual(expect.arrayContaining(['B3_TAXA_SWAP_DCL', 'B3_TAXA_SWAP_PTX']));
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
