import { formatarDataAAMMDD } from '../data-aammdd.js';

/**
 * Monta a URL real de download do arquivo de mercado secundário da ANBIMA.
 * Endpoint confirmado por captura real de rede do usuário (2026-08-22):
 * `https://www.anbima.com.br/informacoes/merc-sec/arqs/ms<AAMMDD>.txt`.
 */
export function urlDownloadAnbima(referenceDate: string): string {
  return `https://www.anbima.com.br/informacoes/merc-sec/arqs/ms${formatarDataAAMMDD(referenceDate)}.txt`;
}
