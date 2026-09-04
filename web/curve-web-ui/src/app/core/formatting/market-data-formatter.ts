/**
 * Utilitários de formatação de valores de mercado.
 * IMPORTANTE: Nunca utiliza parseFloat ou Number() em valores de mercado financeiros,
 * preservando todos os dígitos recebidos do BFF como string pura.
 */

export class MarketDataFormatter {
  /**
   * Formata taxa para exibição em tela trocando ponto por vírgula e opcionalmente adicionando '%'
   */
  public static formatarTaxa(taxa: string | undefined | null, incluirPercent: boolean = true): string {
    if (!taxa || typeof taxa !== 'string' || taxa.trim() === '') {
      return '-';
    }

    const valorLimpo = taxa.trim();
    const comVirgula = valorLimpo.replace('.', ',');

    return incluirPercent ? `${comVirgula} %` : comVirgula;
  }

  /**
   * Formata fator de desconto para exibição em tela preservando todas as casas decimais
   */
  public static formatarFatorDesconto(fator: string | undefined | null): string {
    if (!fator || typeof fator !== 'string' || fator.trim() === '') {
      return '-';
    }

    return fator.trim().replace('.', ',');
  }

  /**
   * Formata spread ou diferença de taxa em basis points (bps)
   */
  public static formatarBps(diferencaBps: string | undefined | null): string {
    if (!diferencaBps || typeof diferencaBps !== 'string' || diferencaBps.trim() === '') {
      return '-';
    }

    const valorLimpo = diferencaBps.trim();
    const prefixo = valorLimpo.startsWith('-') || valorLimpo.startsWith('+') ? '' : '+';
    const comVirgula = valorLimpo.replace('.', ',');

    return `${prefixo}${comVirgula} bps`;
  }
}
