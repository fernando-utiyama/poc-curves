import { describe, expect, it } from 'vitest';
import { dividirLinhasEmBlocos, extrairLinhas } from './linhas.js';

describe('extrairLinhas', () => {
  it('separa linhas terminadas em CRLF, sem incluir o \\r', () => {
    const conteudo = Buffer.from('linha1\r\nlinha2\r\nlinha3', 'utf-8');
    const linhas = extrairLinhas(conteudo);

    expect(linhas.map((l) => l.toString('utf-8'))).toEqual(['linha1', 'linha2', 'linha3']);
  });

  it('separa linhas terminadas só em LF', () => {
    const conteudo = Buffer.from('a\nb\nc', 'utf-8');
    const linhas = extrairLinhas(conteudo);

    expect(linhas.map((l) => l.toString('utf-8'))).toEqual(['a', 'b', 'c']);
  });

  it('preserva linha em branco entre duas linhas com conteúdo', () => {
    const conteudo = Buffer.from('a\r\n\r\nb', 'utf-8');
    const linhas = extrairLinhas(conteudo);

    expect(linhas.map((l) => l.toString('utf-8'))).toEqual(['a', '', 'b']);
  });

  it('não perde a última linha quando o arquivo termina sem terminador', () => {
    const conteudo = Buffer.from('a\r\nb', 'utf-8');
    const linhas = extrairLinhas(conteudo);

    expect(linhas.map((l) => l.toString('utf-8'))).toEqual(['a', 'b']);
  });

  it('não gera linha vazia extra quando o arquivo termina com terminador', () => {
    const conteudo = Buffer.from('a\r\nb\r\n', 'utf-8');
    const linhas = extrairLinhas(conteudo);

    expect(linhas.map((l) => l.toString('utf-8'))).toEqual(['a', 'b']);
  });

  it('preserva os bytes originais byte a byte (sem reencode)', () => {
    const conteudo = Buffer.from([0x41, 0x0d, 0x0a, 0xe9, 0x0d, 0x0a]); // "A\r\n" + é(latin1) + "\r\n"
    const linhas = extrairLinhas(conteudo);

    expect(linhas).toEqual([Buffer.from([0x41]), Buffer.from([0xe9])]);
  });
});

describe('dividirLinhasEmBlocos', () => {
  it('pula as linhas de cabeçalho e divide o resto em blocos, reproduzindo a forma real do arquivo ANBIMA (título, linha em branco, cabeçalho, dados)', () => {
    const conteudo = Buffer.from(
      'ANBIMA - Titulo\r\n\r\nTitulo@Data@Tx\r\nLTN@20260821@13,75\r\nLTN@20260821@13,58\r\nLFT@20260821@0,03\r\n',
      'utf-8',
    );

    const blocos = dividirLinhasEmBlocos(conteudo, 2, 3);

    expect(blocos).toHaveLength(2);
    expect(blocos[0]?.registros.map((r) => r.toString('utf-8'))).toEqual([
      'LTN@20260821@13,75',
      'LTN@20260821@13,58',
    ]);
    expect(blocos[1]?.registros.map((r) => r.toString('utf-8'))).toEqual(['LFT@20260821@0,03']);
  });

  it('lança erro quando não sobra nenhuma linha de dado', () => {
    const conteudo = Buffer.from('titulo\r\n\r\ncabecalho\r\n', 'utf-8');

    expect(() => dividirLinhasEmBlocos(conteudo, 10, 3)).toThrow('nenhuma linha de dado');
  });
});
