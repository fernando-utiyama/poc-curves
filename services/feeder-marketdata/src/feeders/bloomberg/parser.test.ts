import { describe, expect, it } from 'vitest';
import { parsearArquivoBloomberg } from './parser.js';

describe('parsearArquivoBloomberg', () => {
  it('a. arquivo com 3 linhas válidas (JUROS, CAMBIO, INFLACAO): devolve 3 registros com campos corretos e valor preservado como string', () => {
    const csv = [
      'JUROS;SWAP;USSW5 Curncy;PX_LAST;4.523;2026-08-21',
      'CAMBIO;SPOT;USDBRL Curncy;PX_LAST;5.4231;2026-08-21',
      'INFLACAO;SWAP;BZBI 1Y Index;PX_LAST;4.15;2026-08-21',
    ].join('\n');
    const conteudo = Buffer.from(csv, 'utf-8');

    const registros = parsearArquivoBloomberg(conteudo, 'utf-8');

    expect(registros).toHaveLength(3);
    expect(registros[0]).toEqual({
      classeAtivo: 'JUROS',
      tipoInstrumento: 'SWAP',
      ticker: 'USSW5 Curncy',
      campo: 'PX_LAST',
      valor: '4.523',
      dataReferencia: '2026-08-21',
    });
    expect(typeof registros[0]?.valor).toBe('string');

    expect(registros[1]).toEqual({
      classeAtivo: 'CAMBIO',
      tipoInstrumento: 'SPOT',
      ticker: 'USDBRL Curncy',
      campo: 'PX_LAST',
      valor: '5.4231',
      dataReferencia: '2026-08-21',
    });
    expect(typeof registros[1]?.valor).toBe('string');

    expect(registros[2]).toEqual({
      classeAtivo: 'INFLACAO',
      tipoInstrumento: 'SWAP',
      ticker: 'BZBI 1Y Index',
      campo: 'PX_LAST',
      valor: '4.15',
      dataReferencia: '2026-08-21',
    });
    expect(typeof registros[2]?.valor).toBe('string');
  });

  it('b. arquivo com uma linha com número de campos errado: lança Error nomeando o número da linha', () => {
    const csv = [
      'JUROS;SWAP;USSW5 Curncy;PX_LAST;4.523;2026-08-21',
      'CAMBIO;SPOT;USDBRL Curncy;PX_LAST;5.4231', // apenas 5 campos
      'INFLACAO;SWAP;BZBI 1Y Index;PX_LAST;4.15;2026-08-21',
    ].join('\n');
    const conteudo = Buffer.from(csv, 'utf-8');

    expect(() => parsearArquivoBloomberg(conteudo, 'utf-8')).toThrowError(
      /linha 2/i,
    );
  });

  it('c. linhas em branco entre registros são ignoradas', () => {
    const csv = [
      '',
      'JUROS;SWAP;USSW5 Curncy;PX_LAST;4.523;2026-08-21',
      '   ',
      'CAMBIO;SPOT;USDBRL Curncy;PX_LAST;5.4231;2026-08-21',
      '',
    ].join('\n');
    const conteudo = Buffer.from(csv, 'utf-8');

    const registros = parsearArquivoBloomberg(conteudo, 'utf-8');

    expect(registros).toHaveLength(2);
    expect(registros[0]?.ticker).toBe('USSW5 Curncy');
    expect(registros[1]?.ticker).toBe('USDBRL Curncy');
  });
});
