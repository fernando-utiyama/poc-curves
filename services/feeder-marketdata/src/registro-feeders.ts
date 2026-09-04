import type { Feeder } from './feeder.js';

/** Lançado quando nenhum Feeder está registrado para o dataset pedido. */
export class DatasetNaoSuportadoError extends Error {
  constructor(dataset: string) {
    super(`dataset não suportado: ${dataset}`);
    this.name = 'DatasetNaoSuportadoError';
  }
}

/**
 * Registro de Feeder por nome de dataset (ex.: "PR_DI1", "BVBG.086",
 * "BVBG.028", "CURVA_REFERENCIA"). Resolver um dataset não registrado falha
 * cedo e nomeado — nunca cai num Feeder padrão silenciosamente.
 */
export class RegistroFeeders {
  private readonly porDataset = new Map<string, Feeder>();

  /**
   * @throws Error se já existir um Feeder registrado para este dataset
   */
  registrar(dataset: string, feeder: Feeder): void {
    if (this.porDataset.has(dataset)) {
      throw new Error(`dataset já registrado: ${dataset}`);
    }
    this.porDataset.set(dataset, feeder);
  }

  /**
   * @throws DatasetNaoSuportadoError se não houver Feeder registrado para este dataset
   */
  resolver(dataset: string): Feeder {
    const feeder = this.porDataset.get(dataset);
    if (!feeder) {
      throw new DatasetNaoSuportadoError(dataset);
    }
    return feeder;
  }

  /** Nomes de todos os datasets atualmente registrados, em nenhuma ordem garantida. */
  datasetsRegistrados(): string[] {
    return [...this.porDataset.keys()];
  }
}
