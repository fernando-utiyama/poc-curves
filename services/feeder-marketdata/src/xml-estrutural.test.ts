import { describe, expect, it } from 'vitest';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';
import { extrairElementosRepetidos, dividirXmlEmBlocos } from './xml-estrutural.js';

const __dirname = dirname(fileURLToPath(import.meta.url));
const fixturePRPath = join(__dirname, '..', 'fixtures', 'BVBG.086.01_fixture.xml');
const fixtureINPath = join(__dirname, '..', 'fixtures', 'BVBG.028.02_fixture.xml');

const conteudoDoFixturePR = readFileSync(fixturePRPath);
const conteudoDoFixtureIN = readFileSync(fixtureINPath);

describe('extrairElementosRepetidos', () => {
  it('retorna exatamente 5 buffers ao extrair BizGrp do fixture BVBG.086.01', () => {
    const elementos = extrairElementosRepetidos(conteudoDoFixturePR, 'BizGrp');
    expect(elementos).toHaveLength(5);
  });

  it('preserva tags de abertura e fechamento em cada elemento extraído', () => {
    const elementos = extrairElementosRepetidos(conteudoDoFixturePR, 'BizGrp');
    for (const elemento of elementos) {
      const texto = elemento.toString('utf-8');
      expect(texto.startsWith('<BizGrp>')).toBe(true);
      expect(texto.endsWith('</BizGrp>')).toBe(true);
    }
  });

  it('extrai os elementos contendo os tickers reais na ordem de BVBG.086.01', () => {
    const elementos = extrairElementosRepetidos(conteudoDoFixturePR, 'BizGrp');
    const tickersEsperados = ['TTENT131', 'TTENT141', 'TTENT151', 'TTENT161', 'XPBRI106'];

    expect(elementos).toHaveLength(tickersEsperados.length);
    tickersEsperados.forEach((ticker, index) => {
      expect(elementos[index]?.toString('utf-8').includes(ticker)).toBe(true);
    });
  });

  it('retorna 5 buffers contendo os tickers reais na ordem de BVBG.028.02', () => {
    const elementos = extrairElementosRepetidos(conteudoDoFixtureIN, 'BizGrp');
    const tickersEsperados = ['NVDCE202', 'HAPVT200', 'NVDCE19', 'ORVRV39', 'ENEVX270'];

    expect(elementos).toHaveLength(tickersEsperados.length);
    tickersEsperados.forEach((ticker, index) => {
      expect(elementos[index]?.toString('utf-8').includes(ticker)).toBe(true);
    });
  });

  it('retorna array vazio quando o conteúdo é válido mas não contém o elemento procurado', () => {
    const elementos = extrairElementosRepetidos(
      Buffer.from('<Documento><Outro>x</Outro></Documento>', 'utf-8'),
      'BizGrp',
    );
    expect(elementos).toEqual([]);
  });

  it('lança erro quando há tag de abertura sem a tag de fechamento correspondente', () => {
    expect(() =>
      extrairElementosRepetidos(Buffer.from('<BizGrp>sem fechamento', 'utf-8'), 'BizGrp'),
    ).toThrowError(
      'tag de abertura "<BizGrp>" sem fechamento correspondente a partir da posição 0',
    );
  });
});

describe('preservação de encoding e de separador decimal (não conversão)', () => {
  function acharElementoNVDCE202(): Buffer {
    const elementos = extrairElementosRepetidos(conteudoDoFixtureIN, 'BizGrp');
    const elemento = elementos.find((e) => e.toString('utf-8').includes('NVDCE202'));
    expect(elemento).toBeDefined();
    return elemento as Buffer;
  }

  it('preserva byte a byte o valor com separador decimal em ponto (ExrcPric, real do BVBG.028.02)', () => {
    const texto = acharElementoNVDCE202().toString('utf-8');
    expect(texto).toContain('<ExrcPric Ccy="BRL">20.23</ExrcPric>');
  });

  it('preserva byte a byte o mesmo valor com separador decimal em vírgula, dentro de um campo de texto livre (Desc, real do BVBG.028.02)', () => {
    const texto = acharElementoNVDCE202().toString('utf-8');
    expect(texto).toContain('20,23');
  });

  it('não converte nenhum dos dois separadores decimais — ambos coexistem intactos no mesmo elemento, como no arquivo real', () => {
    const texto = acharElementoNVDCE202().toString('utf-8');
    // A B3 usa ponto (ExrcPric) e vírgula (Desc) para o mesmo valor 20,23 no
    // mesmo registro real — se o feeder convertesse separador decimal, um
    // dos dois teria que virar o outro. Nenhum vira: extração é corte
    // estrutural de bytes, nunca parsing/reformatação de conteúdo.
    expect(texto).toContain('20.23');
    expect(texto).toContain('20,23');
  });

  it('não altera nenhum byte do trecho extraído em relação ao arquivo original (comparação binária)', () => {
    const elementos = extrairElementosRepetidos(conteudoDoFixtureIN, 'BizGrp');
    const primeiroElemento = elementos[0];
    expect(primeiroElemento).toBeDefined();

    const posicaoNoArquivo = conteudoDoFixtureIN.indexOf(primeiroElemento as Buffer);

    expect(posicaoNoArquivo).toBeGreaterThanOrEqual(0);
    expect(
      conteudoDoFixtureIN.subarray(
        posicaoNoArquivo,
        posicaoNoArquivo + (primeiroElemento as Buffer).length,
      ),
    ).toEqual(primeiroElemento);
  });

  it('não altera nenhum dígito de identificadores numéricos reais (FinInstrmId), sem zeros à esquerda removidos nem reformatação', () => {
    const texto = acharElementoNVDCE202().toString('utf-8');
    expect(texto).toContain('<Id>200002710436</Id>');
  });
});

describe('dividirXmlEmBlocos', () => {
  it('divide o conteúdo em blocos com tamanhos 2, 2 e 1 preservando a ordem e integridade', () => {
    const blocos = dividirXmlEmBlocos(conteudoDoFixturePR, 'BizGrp', 2);

    expect(blocos).toHaveLength(3);

    expect(blocos[0]?.sequencia).toBe(1);
    expect(blocos[0]?.totalBlocos).toBe(3);
    expect(blocos[0]?.registros).toHaveLength(2);

    expect(blocos[1]?.sequencia).toBe(2);
    expect(blocos[1]?.totalBlocos).toBe(3);
    expect(blocos[1]?.registros).toHaveLength(2);

    expect(blocos[2]?.sequencia).toBe(3);
    expect(blocos[2]?.totalBlocos).toBe(3);
    expect(blocos[2]?.registros).toHaveLength(1);

    const elementosDiretos = extrairElementosRepetidos(conteudoDoFixturePR, 'BizGrp');
    const elementosConcatenados = blocos.flatMap((bloco) => bloco.registros);
    expect(elementosConcatenados).toEqual(elementosDiretos);
  });

  it('retorna um único bloco quando o tamanho do bloco é maior que o número de elementos', () => {
    const blocos = dividirXmlEmBlocos(conteudoDoFixturePR, 'BizGrp', 10);
    const elementosDiretos = extrairElementosRepetidos(conteudoDoFixturePR, 'BizGrp');

    expect(blocos).toHaveLength(1);
    expect(blocos[0]).toEqual({
      sequencia: 1,
      totalBlocos: 1,
      registros: elementosDiretos,
    });
  });

  it('lança erro quando nenhum elemento repetido é encontrado no XML', () => {
    expect(() =>
      dividirXmlEmBlocos(Buffer.from('<Documento></Documento>', 'utf-8'), 'BizGrp', 5),
    ).toThrowError('nenhum elemento <BizGrp> encontrado no XML');
  });
});
