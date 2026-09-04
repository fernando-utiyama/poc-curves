import { dividirEmBlocos, type Bloco } from './blocos.js';

const CR = 0x0d;
const LF = 0x0a;

/**
 * Extrai as linhas de um conteúdo, como corte estrutural por bytes — nunca
 * decodifica o conteúdo inteiro para `string` primeiro (mesmo motivo de
 * `xml-estrutural.ts`: preservar byte a byte, e não presumir que o arquivo
 * inteiro cabe dentro do limite de comprimento de string do V8). Aceita
 * terminador `\n` ou `\r\n` (real: a ANBIMA usa CRLF) — a barra `\r` final,
 * se houver, é removida de cada linha.
 */
export function extrairLinhas(conteudo: Buffer): Buffer[] {
  const linhas: Buffer[] = [];
  let inicio = 0;

  for (let i = 0; i < conteudo.length; i++) {
    if (conteudo[i] === LF) {
      let fim = i;
      if (fim > inicio && conteudo[fim - 1] === CR) {
        fim--;
      }
      linhas.push(conteudo.subarray(inicio, fim));
      inicio = i + 1;
    }
  }
  if (inicio < conteudo.length) {
    linhas.push(conteudo.subarray(inicio));
  }

  return linhas;
}

/**
 * Extrai as linhas e as divide em blocos de até `tamanhoBloco` cada, pulando
 * `linhasParaIgnorar` do início (ex.: título e cabeçalho de um arquivo
 * delimitado) e linhas em branco.
 *
 * @throws Error se sobrar zero linhas de dado após pular o cabeçalho e as linhas em branco
 */
export function dividirLinhasEmBlocos(
  conteudo: Buffer,
  tamanhoBloco: number,
  linhasParaIgnorar: number,
): Bloco<Buffer>[] {
  const todasAsLinhas = extrairLinhas(conteudo);
  const linhasDeDado = todasAsLinhas.slice(linhasParaIgnorar).filter((linha) => linha.length > 0);

  if (linhasDeDado.length === 0) {
    throw new Error('nenhuma linha de dado encontrada após o cabeçalho');
  }

  return dividirEmBlocos(linhasDeDado, tamanhoBloco);
}
