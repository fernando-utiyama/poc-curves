import AdmZip from 'adm-zip';
import type { ResultadoIntegridade } from './integridade.js';

/** Uma entrada de arquivo dentro de um ZIP, com seu nome e data de modificação. */
export interface EntradaZip {
  readonly nome: string;
  readonly modificadoEm: Date;
}

/**
 * Confere que o conteúdo é um arquivo ZIP bem formado e abre corretamente.
 * Download truncado ou arquivo corrompido falha aqui, antes de qualquer
 * tentativa de extração. Os downloads reais da B3 (verificados nesta sessão
 * contra `PR260821.zip`/`IN260821.zip` reais do pregão de 2026-08-21) são
 * arquivos ZIP — não gzip; `verificarArquivoCompactado` (integridade.ts) não
 * se aplica a eles.
 * <p>
 * Um ZIP bem formado com ZERO entradas é `valido: true` aqui — é
 * estruturalmente um ZIP correto, não corrompido. Confirmado de verdade
 * contra o endpoint real da B3: um arquivo ainda não publicado responde
 * HTTP 200 com um ZIP válido, porém vazio (22 bytes, 0 entradas) — esse é o
 * sinal de "sem dado disponível", não de arquivo corrompido; quem decide o
 * que fazer com um ZIP vazio é a lógica de aquisição
 * ({@link lerEntradaMaisRecente} lança nesse caso), não esta verificação de
 * integridade estrutural.
 */
export function verificarArquivoZip(conteudo: Buffer): ResultadoIntegridade {
  try {
    new AdmZip(conteudo);
    return { valido: true };
  } catch (erro) {
    const mensagem = erro instanceof Error ? erro.message : String(erro);
    return { valido: false, motivo: `arquivo ZIP corrompido ou inválido: ${mensagem}` };
  }
}

/** Lista as entradas de um ZIP (nome e data de modificação), sem extrair conteúdo. */
export function listarEntradas(conteudo: Buffer): EntradaZip[] {
  const zip = new AdmZip(conteudo);
  return zip.getEntries().map((entrada) => ({
    nome: entrada.entryName,
    modificadoEm: entrada.header.time,
  }));
}

/**
 * Lê a entrada mais recente (maior data de modificação) do ZIP. A B3
 * publica múltiplos arquivos dentro do mesmo ZIP — revisões intraday do
 * mesmo pregão — verificado com os arquivos reais de 2026-08-21:
 * `PR260821.zip` contém 4 XMLs (18:42, 19:09, 19:22, 20:37 UTC-3),
 * `IN260821.zip` contém 2 (00:20, 18:39 UTC-3). A entrada com a data de
 * modificação mais recente é a revisão final do dia — a decisão de qual
 * entrada usar quando há mais de uma.
 *
 * @throws Error se o ZIP não tiver nenhuma entrada
 */
export function lerEntradaMaisRecente(conteudo: Buffer): { nome: string; dados: Buffer } {
  const zip = new AdmZip(conteudo);
  const entradas = zip.getEntries();
  if (entradas.length === 0) {
    throw new Error('arquivo ZIP não contém nenhuma entrada');
  }

  const maisRecente = entradas.reduce((a, b) => (a.header.time > b.header.time ? a : b));
  const dados = zip.readFile(maisRecente);
  if (dados === null) {
    throw new Error(`falha ao ler a entrada "${maisRecente.entryName}" do ZIP`);
  }

  return { nome: maisRecente.entryName, dados };
}
