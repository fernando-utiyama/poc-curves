import { createHash } from 'node:crypto';
import { v5 as uuidv5 } from 'uuid';

/**
 * Namespace UUID fixo da aplicação para geração determinística de loteId e
 * eventId via UUIDv5 (RFC 4122). Constante — NUNCA deve mudar; mudar o
 * namespace muda todo loteId/eventId já calculado para o mesmo conteúdo.
 */
export const APP_NAMESPACE = '2a973c65-6096-4a59-bda3-c91bb9947d6f';

/**
 * Calcula o loteId determinístico de um lote de ingestão: UUIDv5 derivado de
 * source + dataset + referenceDate + sha256(conteúdo completo adquirido).
 * Mesmo conteúdo e mesmos metadados sempre produzem o mesmo loteId — é o que
 * garante que blocos de um mesmo lote reenviado caiam na mesma partição
 * Kafka (chave de partição = source|dataset|referenceDate) e permite ao
 * consumidor detectar retransmissão.
 */
export function calcularLoteId(
  source: string,
  dataset: string,
  referenceDate: string,
  conteudo: Buffer | string,
): string {
  const hashConteudo = createHash('sha256').update(conteudo).digest('hex');
  const nome = `${source}|${dataset}|${referenceDate}|${hashConteudo}`;
  return uuidv5(nome, APP_NAMESPACE);
}

/**
 * Calcula o eventId determinístico de um bloco dentro de um lote: UUIDv5
 * derivado de loteId + sequencia. Determinístico por design — reenviar o
 * mesmo bloco do mesmo lote produz o mesmo eventId, permitindo idempotência
 * no consumidor (dedup por eventId).
 */
export function calcularEventId(loteId: string, sequencia: number): string {
  const nome = `${loteId}:${sequencia}`;
  return uuidv5(nome, APP_NAMESPACE);
}
