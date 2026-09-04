import { describe, expect, it } from 'vitest';
import { criarEnvelope, type ParametrosCriarEnvelope } from './envelope.js';

describe('criarEnvelope', () => {
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

  it('retorna um envelope com source B3 com todos os campos e sem a chave submittedBy', () => {
    const envelope = criarEnvelope({ ...base, source: 'B3' });

    expect(envelope.eventId).toBe('a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11');
    expect(envelope.correlationId).toBe('b0eebc99-9c0b-4ef8-bb6d-6bb9bd380a22');
    expect(envelope.source).toBe('B3');
    expect(envelope.dataset).toBe('PR_DI1');
    expect(envelope.referenceDate).toBe('2026-08-21');
    expect(envelope.producedAt).toBe('2026-08-21T18:00:00Z');
    expect(envelope.schemaVersion).toBe('1.0');
    expect(envelope.payloadKind).toBe('INDIVIDUAL_QUOTES');
    expect(envelope.loteId).toBe('lote-abc-1');
    expect(envelope.sequencia).toBe(1);
    expect(envelope.totalBlocos).toBe(1);
    expect(envelope.payload).toEqual({});
    expect('submittedBy' in envelope).toBe(false);
  });

  it('retorna envelope MANUAL com submittedBy e justification', () => {
    const envelope = criarEnvelope({
      ...base,
      source: 'MANUAL',
      submittedBy: 'operador.x',
      justification: 'ajuste manual',
    });

    expect(envelope.source).toBe('MANUAL');
    if (envelope.source === 'MANUAL') {
      expect(envelope.submittedBy).toBe('operador.x');
      expect(envelope.justification).toBe('ajuste manual');
    }
  });

  it('lança erro cuja mensagem contém submittedBy quando source é MANUAL sem submittedBy', () => {
    expect(() => criarEnvelope({ ...base, source: 'MANUAL' })).toThrowError(/submittedBy/);
  });

  it('lança erro cuja mensagem contém justification quando source é MANUAL sem justification', () => {
    expect(() =>
      criarEnvelope({
        ...base,
        source: 'MANUAL',
        submittedBy: 'operador.x',
      }),
    ).toThrowError(/justification/);
  });

  it('lança erro quando eventId é vazio', () => {
    expect(() => criarEnvelope({ ...base, source: 'B3', eventId: '' })).toThrowError(
      'eventId não pode ser vazio',
    );
  });

  it('lança erro quando schemaVersion não tem o ponto (formato inválido)', () => {
    expect(() => criarEnvelope({ ...base, source: 'B3', schemaVersion: '1' })).toThrowError(
      /schemaVersion deve seguir o padrão major\.minor/,
    );
  });

  it('lança erro quando sequencia é 0', () => {
    expect(() => criarEnvelope({ ...base, source: 'B3', sequencia: 0 })).toThrowError(
      'sequencia deve ser >= 1: recebido 0',
    );
  });

  it('lança erro quando totalBlocos é 0', () => {
    expect(() => criarEnvelope({ ...base, source: 'B3', totalBlocos: 0 })).toThrowError(
      'totalBlocos deve ser >= 1: recebido 0',
    );
  });
});
