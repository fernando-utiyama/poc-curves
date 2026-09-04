import {
  DATASET_ANBIMA_MERCADO_SECUNDARIO,
  FeederAnbimaMercadoSecundario,
} from './feeders/anbima-mercado-secundario.js';
import type { EnviarMensagem } from './kafka-publisher.js';
import type { RegistroFeeders } from './registro-feeders.js';

/**
 * Registra os feeders ANBIMA em um `RegistroFeeders` já existente — usa o
 * mesmo `RegistroFeeders` que `montarRegistroFeedersB3` (registro-feeders-b3.ts)
 * povoa, então os dois convivem no mesmo roteador de datasets (ver
 * docs/extensao-feeders.md).
 */
export function registrarFeedersAnbima(registro: RegistroFeeders, enviar: EnviarMensagem): void {
  registro.registrar(DATASET_ANBIMA_MERCADO_SECUNDARIO, new FeederAnbimaMercadoSecundario(enviar));
}
