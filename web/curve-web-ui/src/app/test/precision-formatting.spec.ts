import { describe, it, expect } from 'vitest';
import { MarketDataFormatter } from '../core/formatting/market-data-formatter.ts';

describe('MarketDataFormatter — Preservação Estrita de Precisão Numérica', () => {
  it('deve preservar todas as 12 casas decimais de uma taxa sem truncar nem converter para float', () => {
    const taxa12Casas = '14.129384756123';
    const formatada = MarketDataFormatter.formatarTaxa(taxa12Casas, true);
    expect(formatada).toBe('14,129384756123 %');
  });

  it('deve formatar fator de desconto preservando todos os dígitos exatos como string', () => {
    const fator = '0.987654321098';
    const formatado = MarketDataFormatter.formatarFatorDesconto(fator);
    expect(formatado).toBe('0,987654321098');
  });

  it('deve formatar spreads e diferenças de taxa em bps preservando sinal e dígitos', () => {
    expect(MarketDataFormatter.formatarBps('12.3456')).toBe('+12,3456 bps');
    expect(MarketDataFormatter.formatarBps('-5.6789')).toBe('-5,6789 bps');
    expect(MarketDataFormatter.formatarBps('0.0000')).toBe('+0,0000 bps');
  });

  it('deve retornar traço para valores nulos, vazios ou indefinidos', () => {
    expect(MarketDataFormatter.formatarTaxa(null)).toBe('-');
    expect(MarketDataFormatter.formatarTaxa('')).toBe('-');
    expect(MarketDataFormatter.formatarFatorDesconto(undefined)).toBe('-');
    expect(MarketDataFormatter.formatarBps(null)).toBe('-');
  });
});
