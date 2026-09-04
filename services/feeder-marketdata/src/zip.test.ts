import AdmZip from 'adm-zip';
import { describe, expect, it } from 'vitest';
import { lerEntradaMaisRecente, listarEntradas, verificarArquivoZip } from './zip.js';

function construirZip(
  entradas: ReadonlyArray<{ nome: string; conteudo: string; data: Date }>,
): Buffer {
  const zip = new AdmZip();
  for (const { nome, conteudo, data } of entradas) {
    const entrada = zip.addFile(nome, Buffer.from(conteudo, 'utf-8'));
    entrada.header.time = data;
  }
  return zip.toBuffer();
}

describe('verificarArquivoZip', () => {
  it('retorna valido: true para um ZIP real com uma entrada', () => {
    const zip = construirZip([{ nome: 'a.xml', conteudo: 'conteudo', data: new Date() }]);
    expect(verificarArquivoZip(zip)).toEqual({ valido: true });
  });

  it('retorna valido: false com motivo para conteúdo que não é um ZIP', () => {
    const resultado = verificarArquivoZip(Buffer.from('isto não é um zip'));
    expect(resultado.valido).toBe(false);
    if (!resultado.valido) {
      expect(resultado.motivo.length).toBeGreaterThan(0);
    }
  });

  it('retorna valido: false para ZIP truncado', () => {
    const zipCompleto = construirZip([
      { nome: 'a.xml', conteudo: 'conteudo de teste', data: new Date() },
    ]);
    const truncado = zipCompleto.subarray(0, 10);
    const resultado = verificarArquivoZip(truncado);
    expect(resultado.valido).toBe(false);
  });

  it('retorna valido: true para um ZIP bem formado com ZERO entradas — confirmado real: é assim que a B3 responde para um arquivo ainda não publicado', () => {
    const zipVazio = new AdmZip().toBuffer();
    expect(verificarArquivoZip(zipVazio)).toEqual({ valido: true });
  });
});

describe('listarEntradas', () => {
  it('lista nome e data de modificação de cada entrada', () => {
    const dataA = new Date('2026-08-21T18:42:00Z');
    const dataB = new Date('2026-08-21T20:37:00Z');
    const zip = construirZip([
      { nome: 'BVBG.086.01_a.xml', conteudo: 'A', data: dataA },
      { nome: 'BVBG.086.01_b.xml', conteudo: 'B', data: dataB },
    ]);

    const entradas = listarEntradas(zip);

    expect(entradas).toHaveLength(2);
    expect(entradas.map((e) => e.nome).sort()).toEqual(['BVBG.086.01_a.xml', 'BVBG.086.01_b.xml']);
    const entradaA = entradas.find((e) => e.nome === 'BVBG.086.01_a.xml');
    expect(entradaA?.modificadoEm.toISOString()).toBe(dataA.toISOString());
  });
});

describe('lerEntradaMaisRecente', () => {
  it('lê a entrada com a data de modificação mais recente, reproduzindo o caso real: PR260821.zip com 4 revisões intraday', () => {
    const zip = construirZip([
      {
        nome: 'BVBG.086.01_rev1.xml',
        conteudo: 'revisão 18:42',
        data: new Date('2026-08-21T18:42:00Z'),
      },
      {
        nome: 'BVBG.086.01_rev2.xml',
        conteudo: 'revisão 19:09',
        data: new Date('2026-08-21T19:09:00Z'),
      },
      {
        nome: 'BVBG.086.01_rev3.xml',
        conteudo: 'revisão 19:22',
        data: new Date('2026-08-21T19:22:00Z'),
      },
      {
        nome: 'BVBG.086.01_rev4_final.xml',
        conteudo: 'revisão 20:37 final',
        data: new Date('2026-08-21T20:37:00Z'),
      },
    ]);

    const resultado = lerEntradaMaisRecente(zip);

    expect(resultado.nome).toBe('BVBG.086.01_rev4_final.xml');
    expect(resultado.dados.toString('utf-8')).toBe('revisão 20:37 final');
  });

  it('lê a única entrada quando o ZIP tem só uma', () => {
    const zip = construirZip([{ nome: 'unica.xml', conteudo: 'conteudo único', data: new Date() }]);

    const resultado = lerEntradaMaisRecente(zip);

    expect(resultado.nome).toBe('unica.xml');
    expect(resultado.dados.toString('utf-8')).toBe('conteudo único');
  });

  it('preserva os bytes originais byte a byte (sem reencode)', () => {
    const bytesOriginais = Buffer.from([0x00, 0xff, 0x10, 0x20, 0x30, 0xfe]);
    const zip = new AdmZip();
    zip.addFile('binario.dat', bytesOriginais);
    const buf = zip.toBuffer();

    const resultado = lerEntradaMaisRecente(buf);

    expect(resultado.dados).toEqual(bytesOriginais);
  });
});
