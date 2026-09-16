import { formatarDataAAMMDD } from '../data-aammdd.js';

/**
 * Monta a URL real de download do endpoint `pesquisapregao` da B3.
 * Endpoint confirmado por captura real de rede do usuário (2026-08-22):
 * `https://www.b3.com.br/pesquisapregao/download?filelist=<NOME>.<extensao>`.
 * <p>
 * A extensão não é a mesma para todo prefixo — PR/IN usam `.zip` (confirmado
 * ao vivo), mas o arquivo de taxas de swap (`TS`) usa `.ex_` (extensão 8.3
 * truncada, não `.zip` nem `.exe` — a suposição original por analogia com
 * PR/IN estava errada; `.zip`/`.exe` devolvem sempre um ZIP vazio de 22
 * bytes para `TS`, mesmo em datas com pregão real, só `.ex_` devolve o
 * conteúdo de verdade, confirmado ao vivo nesta sessão contra várias datas).
 */
export function urlDownloadB3(prefixoArquivo: string, referenceDate: string, extensao: string = 'zip'): string {
  const nomeArquivo = `${prefixoArquivo}${formatarDataAAMMDD(referenceDate)}.${extensao}`;
  return `https://www.b3.com.br/pesquisapregao/download?filelist=${nomeArquivo}`;
}
