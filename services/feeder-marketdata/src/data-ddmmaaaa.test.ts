import { describe, expect, it } from 'vitest';
import { formatarDataDDMMAAAA } from './data-ddmmaaaa.js';

describe('formatarDataDDMMAAAA', () => {
  it('formata data válida', () => {
    expect(formatarDataDDMMAAAA('2026-08-21')).toBe('21/08/2026');
  });

  it('lança erro para formato inválido', () => {
    expect(() => formatarDataDDMMAAAA('21/08/2026')).toThrowError(
      'referenceDate deve estar no formato YYYY-MM-DD: recebido "21/08/2026"',
    );
  });
});
