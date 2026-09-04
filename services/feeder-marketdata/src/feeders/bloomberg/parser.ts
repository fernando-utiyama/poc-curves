import type { RegistroInstrumentoBruto } from './tipos.js';

/**
 * Corta o arquivo de saída Bloomberg (CSV: classeAtivo;tipoInstrumento;ticker;campo;valor;dataReferencia,
 * uma linha por registro, sem cabeçalho — formato MODELADO, não confirmado
 * contra a Bloomberg real, ver cliente-data-license.ts) em RegistroInstrumentoBruto[].
 * Corte estrutural (uma linha = um registro), nunca interpretação de conteúdo além de
 * separar os campos já delimitados pelo próprio arquivo.
 * @throws Error se alguma linha não tiver exatamente 6 campos
 */
export function parsearArquivoBloomberg(conteudo: Buffer, encoding: string): RegistroInstrumentoBruto[] {
  const texto = new TextDecoder(encoding, { fatal: true }).decode(conteudo);
  const linhas = texto.split('\n').map((l) => l.trim()).filter((l) => l.length > 0);

  return linhas.map((linha, indice) => {
    const campos = linha.split(';');
    if (campos.length !== 6) {
      throw new Error(`linha ${indice + 1} do arquivo Bloomberg com número de campos inesperado (esperado 6, recebido ${campos.length}): "${linha}"`);
    }
    const [classeAtivo, tipoInstrumento, ticker, campo, valor, dataReferencia] = campos;
    return { classeAtivo, tipoInstrumento, ticker, campo, valor, dataReferencia } as RegistroInstrumentoBruto;
  });
}
