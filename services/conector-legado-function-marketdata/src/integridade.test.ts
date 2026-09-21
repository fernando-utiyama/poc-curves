import { gzipSync } from 'node:zlib';
import { describe, expect, it } from 'vitest';
import {
  verificarArquivoCompactado,
  verificarConteudoNaoVazio,
  verificarTamanhoDeclarado,
} from './integridade.js';

describe('verificarConteudoNaoVazio', () => {
  it('retorna valido: false com motivo contendo "vazio" para buffer vazio', () => {
    const resultado = verificarConteudoNaoVazio(Buffer.alloc(0));

    expect(resultado.valido).toBe(false);
    if (!resultado.valido) {
      expect(resultado.motivo).toContain('vazio');
    }
  });

  it('retorna valido: true para buffer não vazio', () => {
    const resultado = verificarConteudoNaoVazio(Buffer.from('dados de teste'));

    expect(resultado).toEqual({ valido: true });
  });
});

describe('verificarTamanhoDeclarado', () => {
  it('retorna valido: true quando o tamanho do buffer bate com o tamanho declarado', () => {
    const resultado = verificarTamanhoDeclarado(Buffer.from('12345'), 5);

    expect(resultado).toEqual({ valido: true });
  });

  it('retorna valido: false com motivo contendo o tamanho real e o declarado quando não batem', () => {
    const resultado = verificarTamanhoDeclarado(Buffer.from('12345'), 10);

    expect(resultado.valido).toBe(false);
    if (!resultado.valido) {
      expect(resultado.motivo).toContain('5');
      expect(resultado.motivo).toContain('10');
    }
  });
});

describe('verificarArquivoCompactado', () => {
  it('retorna valido: true para gzip válido gerado no próprio teste', () => {
    const gzipValido = gzipSync(Buffer.from('conteudo real de teste'));
    const resultado = verificarArquivoCompactado(gzipValido);

    expect(resultado).toEqual({ valido: true });
  });

  it('retorna valido: false com motivo presente para buffer não comprimido', () => {
    const resultado = verificarArquivoCompactado(Buffer.from('isto não é gzip'));

    expect(resultado.valido).toBe(false);
    if (!resultado.valido) {
      expect(resultado.motivo).toBeDefined();
      expect(resultado.motivo.length).toBeGreaterThan(0);
    }
  });

  it('retorna valido: false para gzip truncado', () => {
    const gzipTruncado = gzipSync(Buffer.from('conteudo de teste')).subarray(0, 5);
    const resultado = verificarArquivoCompactado(gzipTruncado);

    expect(resultado.valido).toBe(false);
  });
});
