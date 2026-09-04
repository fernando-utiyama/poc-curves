import { dividirEmBlocos, type Bloco } from './blocos.js';

/**
 * Extrai as substrings XML de cada ocorrência de `<nomeElemento>...</nomeElemento>`
 * no conteúdo, sem fazer parsing semântico do conteúdo interno — é o corte
 * estrutural do D1b do design.md: "este XML tem N elementos repetidos",
 * nunca "isto é uma taxa ou um vencimento". Cada `Buffer` retornado é o
 * elemento completo, incluindo as tags de abertura e fechamento, preservado
 * byte a byte do original (preserva encoding e formato — tarefa 3.6).
 * <p>
 * Opera sobre `Buffer`, nunca decodifica o conteúdo inteiro para `string` —
 * um arquivo real da B3 (BVBG.028, cadastro) chega a ~800MB, acima do limite
 * de comprimento de string do V8 (~536MB, `ERR_STRING_TOO_LONG`), verificado
 * de verdade nesta sessão contra o arquivo real do pregão de 2026-08-21. A
 * busca é por sequência de bytes (`Buffer.indexOf`), o que é seguro mesmo em
 * UTF-8 multi-byte: as tags de abertura/fechamento são ASCII puro, e um byte
 * ASCII nunca aparece como byte de continuação de um caractere multi-byte
 * UTF-8 — cortar exatamente nessas posições nunca parte um caractere ao meio.
 * <p>
 * Não lida com elementos aninhados do mesmo nome dentro de si mesmos (não é
 * o caso de BizGrp nos arquivos reais da B3) nem com a tag aparecendo como
 * texto literal dentro de CDATA.
 *
 * @throws Error se houver uma tag de abertura sem a tag de fechamento correspondente
 */
export function extrairElementosRepetidos(conteudo: Buffer, nomeElemento: string): Buffer[] {
  const tagAbertura = Buffer.from(`<${nomeElemento}>`, 'utf-8');
  const tagFechamento = Buffer.from(`</${nomeElemento}>`, 'utf-8');
  const elementos: Buffer[] = [];
  let cursor = 0;

  while (true) {
    const inicio = conteudo.indexOf(tagAbertura, cursor);
    if (inicio === -1) {
      break;
    }
    const fim = conteudo.indexOf(tagFechamento, inicio);
    if (fim === -1) {
      throw new Error(
        `tag de abertura "<${nomeElemento}>" sem fechamento correspondente a partir da posição ${inicio}`,
      );
    }
    const fimCompleto = fim + tagFechamento.length;
    elementos.push(conteudo.subarray(inicio, fimCompleto));
    cursor = fimCompleto;
  }

  return elementos;
}

/**
 * Extrai os elementos repetidos e os divide em blocos de até `tamanhoBloco`
 * cada, reaproveitando a mesma lógica de divisão de blocos.ts.
 *
 * @throws Error se não houver nenhum elemento `nomeElemento` no conteúdo, ou os
 *               mesmos erros de dividirEmBlocos/extrairElementosRepetidos
 */
export function dividirXmlEmBlocos(
  conteudo: Buffer,
  nomeElemento: string,
  tamanhoBloco: number,
): Bloco<Buffer>[] {
  const elementos = extrairElementosRepetidos(conteudo, nomeElemento);
  if (elementos.length === 0) {
    throw new Error(`nenhum elemento <${nomeElemento}> encontrado no XML`);
  }
  return dividirEmBlocos(elementos, tamanhoBloco);
}
