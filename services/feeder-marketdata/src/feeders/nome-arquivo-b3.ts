import { formatarDataAAMMDD } from '../data-aammdd.js';

/**
 * Monta a URL real de download do endpoint `pesquisapregao` da B3.
 * Endpoint confirmado por captura real de rede do usuário (2026-08-22):
 * `https://www.b3.com.br/pesquisapregao/download?filelist=<NOME>.zip`.
 */
export function urlDownloadB3(prefixoArquivo: string, referenceDate: string): string {
  const nomeArquivo = `${prefixoArquivo}${formatarDataAAMMDD(referenceDate)}.zip`;
  return `https://www.b3.com.br/pesquisapregao/download?filelist=${nomeArquivo}`;
}
