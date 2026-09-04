import { describe, expect, it } from 'vitest';
import type { ParametrosCriarEnvelope } from './envelope.js';
import { criarEnvelope } from './envelope.js';
import { EnvelopeInvalidoError, validarEnvelope } from './validador-envelope.js';

describe('validarEnvelope', () => {
  const base: ParametrosCriarEnvelope = {
    eventId: 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11',
    correlationId: 'b0eebc99-9c0b-4ef8-bb6d-6bb9bd380a22',
    source: 'B3',
    dataset: 'PR_DI1',
    referenceDate: '2026-08-21',
    producedAt: '2026-08-21T18:00:00Z',
    schemaVersion: '1.0',
    payloadKind: 'INDIVIDUAL_QUOTES',
    loteId: 'lote-abc-1',
    sequencia: 1,
    totalBlocos: 1,
    payload: {},
  };

  it('não lança para um envelope conforme ao schema', () => {
    const envelope = criarEnvelope(base);
    expect(() => validarEnvelope(envelope)).not.toThrow();
  });

  it('não lança para um envelope MANUAL com submittedBy e justification presentes', () => {
    const envelope = criarEnvelope({
      ...base,
      source: 'MANUAL',
      submittedBy: 'operador.risco',
      justification: 'reprocessamento após correção de cadastro',
    });
    expect(() => validarEnvelope(envelope)).not.toThrow();
  });

  it('lança EnvelopeInvalidoError quando eventId não é um UUID', () => {
    const envelope = criarEnvelope(base);
    const envelopeComEventIdInvalido = { ...envelope, eventId: 'não-é-um-uuid' };

    expect(() => validarEnvelope(envelopeComEventIdInvalido)).toThrow(EnvelopeInvalidoError);
  });

  it('lança EnvelopeInvalidoError quando referenceDate não está no formato de data', () => {
    const envelope = criarEnvelope(base);
    const envelopeComDataInvalida = { ...envelope, referenceDate: '21/08/2026' };

    expect(() => validarEnvelope(envelopeComDataInvalida)).toThrow(EnvelopeInvalidoError);
  });

  it('mensagem de erro cita o campo inválido', () => {
    const envelope = criarEnvelope(base);
    const envelopeComEventIdInvalido = { ...envelope, eventId: 'não-é-um-uuid' };

    expect(() => validarEnvelope(envelopeComEventIdInvalido)).toThrow(/eventId/);
  });
});
