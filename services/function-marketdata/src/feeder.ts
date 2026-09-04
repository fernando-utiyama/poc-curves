/** Faixa de ingestão do catálogo Kafka (contracts/events/topics.yaml). */
export type Faixa = 'ROTINA' | 'PRIORITARIA' | 'MASSA';

/** Parâmetros de uma solicitação de aquisição de dado por um Feeder. */
export interface AcquisitionParams {
  readonly source: string;
  readonly dataset: string;
  readonly referenceDate: string;
  readonly faixa: Faixa;
  readonly correlationId: string;
}

/**
 * Resultado de três estados de uma aquisição — union discriminada por `kind`.
 * PUBLISHED: dado adquirido e publicado no Kafka com sucesso.
 * NO_DATA: fonte respondeu, mas não há dado disponível para a data pedida
 *   (não é erro — ex.: dia sem pregão, ou fonte ainda não divulgou).
 * FAILED: falha real na aquisição (rede, parsing, integridade), com motivo
 *   resumido e diagnóstico técnico para investigação.
 */
export type AcquisitionResult =
  | { readonly kind: 'PUBLISHED'; readonly loteId: string; readonly totalBlocos: number }
  | { readonly kind: 'NO_DATA'; readonly motivo: string }
  | { readonly kind: 'FAILED'; readonly motivo: string; readonly diagnostico: string };

export function published(loteId: string, totalBlocos: number): AcquisitionResult {
  return { kind: 'PUBLISHED', loteId, totalBlocos };
}

export function noData(motivo: string): AcquisitionResult {
  return { kind: 'NO_DATA', motivo };
}

export function failed(motivo: string, diagnostico: string): AcquisitionResult {
  return { kind: 'FAILED', motivo, diagnostico };
}

/** Contrato que todo feeder de fonte de dado (B3, e no futuro Bloomberg/LSEG) implementa. */
export interface Feeder {
  acquire(params: AcquisitionParams): Promise<AcquisitionResult>;
}
