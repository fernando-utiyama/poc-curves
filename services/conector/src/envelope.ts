/**
 * Tipos TypeScript do envelope de evento, gerados a partir de
 * contracts/events/envelope.schema.json — mesmo contrato de
 * EventEnvelope.java em services/common (Java), aqui em TypeScript.
 */

export type EventSource = 'B3' | 'ANBIMA' | 'BCB' | 'BLOOMBERG' | 'LSEG' | 'MANUAL';
export type PayloadKind = 'INDIVIDUAL_QUOTES' | 'READY_CURVE';

interface CamposComuns {
  readonly eventId: string;
  readonly correlationId: string;
  readonly dataset: string;
  readonly referenceDate: string;
  readonly producedAt: string;
  readonly schemaVersion: string;
  readonly payloadKind: PayloadKind;
  readonly loteId: string;
  readonly sequencia: number;
  readonly totalBlocos: number;
  readonly payload: Record<string, unknown>;
}

/**
 * Envelope de evento — union discriminada por `source`. Quando `source` é
 * `'MANUAL'`, `submittedBy` e `justification` são obrigatórios (regra
 * condicional do contrato); nas outras três origens, esses dois campos nem
 * existem no tipo — o TypeScript recusa acessá-los fora do branch `MANUAL`.
 */
export type EventEnvelope =
  | (CamposComuns & { readonly source: 'B3' | 'ANBIMA' | 'BCB' | 'BLOOMBERG' | 'LSEG' })
  | (CamposComuns & {
      readonly source: 'MANUAL';
      readonly submittedBy: string;
      readonly justification: string;
    });

const SCHEMA_VERSION_PATTERN = /^[0-9]+\.[0-9]+$/;

export interface ParametrosCriarEnvelope {
  readonly eventId: string;
  readonly correlationId: string;
  readonly source: EventSource;
  readonly dataset: string;
  readonly referenceDate: string;
  readonly producedAt: string;
  readonly schemaVersion: string;
  readonly payloadKind: PayloadKind;
  readonly loteId: string;
  readonly sequencia: number;
  readonly totalBlocos: number;
  readonly payload: Record<string, unknown>;
  readonly submittedBy?: string;
  readonly justification?: string;
}

/**
 * Constrói e valida um EventEnvelope, espelhando exatamente as mesmas
 * regras do construtor compacto de EventEnvelope.java em services/common:
 * campos obrigatórios não vazios, schemaVersion no formato major.minor, e
 * submittedBy/justification exigidos quando source é MANUAL.
 */
export function criarEnvelope(params: ParametrosCriarEnvelope): EventEnvelope {
  if (!params.eventId) {
    throw new Error('eventId não pode ser vazio');
  }
  if (!params.correlationId) {
    throw new Error('correlationId não pode ser vazio');
  }
  if (!params.dataset) {
    throw new Error('dataset não pode ser vazio');
  }
  if (!params.referenceDate) {
    throw new Error('referenceDate não pode ser vazio');
  }
  if (!params.producedAt) {
    throw new Error('producedAt não pode ser vazio');
  }
  if (!SCHEMA_VERSION_PATTERN.test(params.schemaVersion)) {
    throw new Error(
      `schemaVersion deve seguir o padrão major.minor (ex.: "1.0"): recebido "${params.schemaVersion}"`,
    );
  }
  if (!params.loteId) {
    throw new Error('loteId não pode ser vazio');
  }
  if (params.sequencia < 1) {
    throw new Error(`sequencia deve ser >= 1: recebido ${params.sequencia}`);
  }
  if (params.totalBlocos < 1) {
    throw new Error(`totalBlocos deve ser >= 1: recebido ${params.totalBlocos}`);
  }

  const camposComuns = {
    eventId: params.eventId,
    correlationId: params.correlationId,
    dataset: params.dataset,
    referenceDate: params.referenceDate,
    producedAt: params.producedAt,
    schemaVersion: params.schemaVersion,
    payloadKind: params.payloadKind,
    loteId: params.loteId,
    sequencia: params.sequencia,
    totalBlocos: params.totalBlocos,
    payload: params.payload,
  };

  if (params.source === 'MANUAL') {
    if (!params.submittedBy) {
      throw new Error('submittedBy não pode ser vazio quando source é MANUAL');
    }
    if (!params.justification) {
      throw new Error('justification não pode ser vazia quando source é MANUAL');
    }
    return {
      ...camposComuns,
      source: 'MANUAL',
      submittedBy: params.submittedBy,
      justification: params.justification,
    };
  }

  return {
    ...camposComuns,
    source: params.source,
  };
}
