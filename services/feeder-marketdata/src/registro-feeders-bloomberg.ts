import { FeederBloomberg, type ConfigFeederBloomberg } from './feeders/bloomberg/feeder-bloomberg.js';
import type { EnviarMensagem } from './kafka-publisher.js';
import type { RegistroFeeders } from './registro-feeders.js';

/**
 * Registra o feeder Bloomberg — chamada SÓ pelo caminho CLI/agendado
 * (main.ts), nunca pelo caminho HTTP síncrono (main-http.ts,
 * azure-function-handler.ts), porque a espera pode levar minutos a horas
 * (ver feeder-bloomberg.ts). Dataset "BLOOMBERG_JUROS_CAMBIO" é o nome
 * provisório do dataset combinado de juros+câmbio+outros insumos — ajustar
 * quando houver definição real do usuário de quais campos/instrumentos
 * pedir.
 */
export function registrarFeedersBloomberg(registro: RegistroFeeders, enviar: EnviarMensagem): void {
  const config: ConfigFeederBloomberg = {
    clienteConfig: {
      baseUrl: process.env['BLOOMBERG_DATA_LICENSE_BASE_URL'] ?? 'https://data-license.bloomberg.example/api/v1',
      httpConfig: { timeoutMs: 30_000, maxRetries: 3, baseBackoffMs: 1_000 },
      maxWaitMs: Number(process.env['BLOOMBERG_MAX_WAIT_MS'] ?? String(4 * 60 * 60 * 1000)), // 4h default
      pollIntervalMs: Number(process.env['BLOOMBERG_POLL_INTERVAL_MS'] ?? String(60 * 1000)), // 1min default
    },
    instrumentos: (process.env['BLOOMBERG_INSTRUMENTOS'] ?? '').split(',').filter((s) => s.length > 0),
    campos: (process.env['BLOOMBERG_CAMPOS'] ?? '').split(',').filter((s) => s.length > 0),
  };
  const feeder = new FeederBloomberg(enviar, config);
  registro.registrar('BLOOMBERG_JUROS_CAMBIO', feeder);
}
