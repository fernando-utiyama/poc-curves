import { gunzipSync } from 'node:zlib';

/** Resultado de uma verificação de integridade — union discriminada por `valido`. */
export type ResultadoIntegridade =
  { readonly valido: true } | { readonly valido: false; readonly motivo: string };

/** Rejeita conteúdo vazio — arquivo de zero bytes nunca é um resultado válido de aquisição. */
export function verificarConteudoNaoVazio(conteudo: Buffer): ResultadoIntegridade {
  if (conteudo.length === 0) {
    return { valido: false, motivo: 'conteúdo vazio' };
  }
  return { valido: true };
}

/** Confere que o tamanho do conteúdo recebido bate com o tamanho declarado pela fonte. */
export function verificarTamanhoDeclarado(
  conteudo: Buffer,
  tamanhoDeclaradoBytes: number,
): ResultadoIntegridade {
  if (conteudo.length !== tamanhoDeclaradoBytes) {
    return {
      valido: false,
      motivo: `tamanho divergente: declarado ${tamanhoDeclaradoBytes} bytes, recebido ${conteudo.length} bytes`,
    };
  }
  return { valido: true };
}

/**
 * Confere que o conteúdo compactado abre corretamente (gzip). Download truncado ou arquivo
 * corrompido falha aqui, antes de qualquer tentativa de parsing do conteúdo descompactado.
 */
export function verificarArquivoCompactado(conteudo: Buffer): ResultadoIntegridade {
  try {
    gunzipSync(conteudo);
    return { valido: true };
  } catch (erro) {
    const mensagem = erro instanceof Error ? erro.message : String(erro);
    return { valido: false, motivo: `arquivo compactado corrompido ou inválido: ${mensagem}` };
  }
}
