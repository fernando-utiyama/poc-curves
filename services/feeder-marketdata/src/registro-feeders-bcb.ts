import { DATASET_BCB_CDI, DATASET_BCB_SELIC, FeederBcbSerieTemporal } from './feeders/bcb-serie-temporal.js';
import type { EnviarMensagem } from './kafka-publisher.js';
import type { RegistroFeeders } from './registro-feeders.js';

export function registrarFeedersBcb(registro: RegistroFeeders, enviar: EnviarMensagem): void {
  registro.registrar(DATASET_BCB_CDI, new FeederBcbSerieTemporal(enviar, 4389));
  registro.registrar(DATASET_BCB_SELIC, new FeederBcbSerieTemporal(enviar, 1178));
}
