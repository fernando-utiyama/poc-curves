import { describe, expect, it } from 'vitest';
import { formatarDataAAMMDD } from './data-aammdd.js';

describe('formatarDataAAMMDD', () => {
  it('formata 2026-08-21 como 260821, igual às capturas reais do usuário (B3 e ANBIMA)', () => {
    expect(formatarDataAAMMDD('2026-08-21')).toBe('260821');
  });

  it('preserva zeros à esquerda em mês e dia de um dígito', () => {
    expect(formatarDataAAMMDD('2026-01-05')).toBe('260105');
  });

  it('lança erro para data fora do formato YYYY-MM-DD', () => {
    expect(() => formatarDataAAMMDD('21/08/2026')).toThrow('YYYY-MM-DD');
    expect(() => formatarDataAAMMDD('2026-8-21')).toThrow('YYYY-MM-DD');
  });
});
