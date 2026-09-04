import { describe, expect, it } from 'vitest';
import { type AcquisitionResult, failed, noData, published } from './feeder.js';

describe('AcquisitionResult factory functions', () => {
  it('published() retorna objeto PUBLISHED com loteId e totalBlocos', () => {
    const result = published('lote-1', 3);

    expect(result).toEqual({
      kind: 'PUBLISHED',
      loteId: 'lote-1',
      totalBlocos: 3,
    });
  });

  it('noData() retorna objeto NO_DATA com motivo', () => {
    const result = noData('sem pregão');

    expect(result).toEqual({
      kind: 'NO_DATA',
      motivo: 'sem pregão',
    });
  });

  it('failed() retorna objeto FAILED com motivo e diagnostico', () => {
    const result = failed('timeout', 'ECONNRESET após 3 tentativas');

    expect(result).toEqual({
      kind: 'FAILED',
      motivo: 'timeout',
      diagnostico: 'ECONNRESET após 3 tentativas',
    });
  });

  it('permite estreitamento de tipo (discriminated union) via switch em result.kind', () => {
    const result: AcquisitionResult = published('lote-1', 3);
    expect.assertions(3);

    switch (result.kind) {
      case 'PUBLISHED': {
        expect(result.kind).toBe('PUBLISHED');
        expect(result.loteId).toBe('lote-1');
        expect(result.totalBlocos).toBe(3);
        break;
      }
      case 'NO_DATA': {
        throw new Error(`Branch inesperado: NO_DATA (${result.motivo})`);
      }
      case 'FAILED': {
        throw new Error(`Branch inesperado: FAILED (${result.motivo}: ${result.diagnostico})`);
      }
      default: {
        const _exhaustive: never = result;
        throw new Error(`Tipo não tratado: ${JSON.stringify(_exhaustive)}`);
      }
    }
  });
});
