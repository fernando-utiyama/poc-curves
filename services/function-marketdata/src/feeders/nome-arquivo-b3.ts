import { formatarDataAAMMDD } from '../data-aammdd.js';

/**
 * Container/fonte usado na convenção de caminho de blob (openspec/changes/raw-file-blob-storage),
 * comum a todos os feeders B3. "b3" sozinho (2 caracteres) viola o mínimo de 3 caracteres exigido
 * pelo Azure Blob Storage para nome de container — confirmado ao vivo contra Azurite real
 * (`RestError: The specified resource name length is not within the permissible limits`).
 */
export const BLOB_CONTAINER_B3 = 'b3-raw';

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
 * Sem valor padrão de propósito — cada família de arquivo deve declarar a
 * sua extensão explicitamente, igual já é feito para `prefixoArquivo`.
 */
export function urlDownloadB3(prefixoArquivo: string, referenceDate: string, extensao: string): string {
  const nomeArquivo = `${prefixoArquivo}${formatarDataAAMMDD(referenceDate)}.${extensao}`;
  return `https://www.b3.com.br/pesquisapregao/download?filelist=${nomeArquivo}`;
}
