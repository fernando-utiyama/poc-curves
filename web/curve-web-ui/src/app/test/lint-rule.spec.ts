import { describe, it, expect } from 'vitest';

describe('Lint Rule — Verificação Estática contra Conversão de Ponto Flutuante', () => {
  const forbiddenPatterns = [
    {
      regex: /parseFloat\s*\(\s*(?:item|vertice|resultado|dto|ponto|curva|dado)?\.?(?:taxa|fatorDesconto|valor|cotacao|taxaA|taxaB)/i,
      description: 'Uso proibido de parseFloat em campo de valor de mercado'
    },
    {
      regex: /Number\s*\(\s*(?:item|vertice|resultado|dto|ponto|curva|dado)?\.?(?:taxa|fatorDesconto|valor|cotacao|taxaA|taxaB)/i,
      description: 'Uso proibido de Number() em campo de valor de mercado'
    }
  ];

  it('deve detectar e rejeitar parseFloat sobre taxas de mercado', () => {
    const linhaInvalida = 'const taxaNum = parseFloat(item.taxa);';
    const violacao = forbiddenPatterns.some(p => p.regex.test(linhaInvalida));
    expect(violacao).toBe(true);
  });

  it('deve detectar e rejeitar Number() sobre fatorDesconto', () => {
    const linhaInvalida = 'const fator = Number(vertice.fatorDesconto);';
    const violacao = forbiddenPatterns.some(p => p.regex.test(linhaInvalida));
    expect(violacao).toBe(true);
  });

  it('deve aceitar formatação segura via MarketDataFormatter e pipes sem conversão float', () => {
    const linhaValida = 'const exibicao = MarketDataFormatter.formatarTaxa(item.taxa);';
    const violacao = forbiddenPatterns.some(p => p.regex.test(linhaValida));
    expect(violacao).toBe(false);
  });
});
