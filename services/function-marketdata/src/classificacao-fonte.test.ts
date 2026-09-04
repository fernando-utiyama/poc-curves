import { describe, expect, it } from 'vitest';
import { classificarRespostaFonte } from './classificacao-fonte.js';

describe('classificarRespostaFonte', () => {
  it('retorna DISPONIVEL quando o status for 200', () => {
    const response = new Response(null, { status: 200 });
    const resultado = classificarRespostaFonte(response);

    expect(resultado).toEqual({ tipo: 'DISPONIVEL' });
  });

  it('retorna NOT_YET_PUBLISHED quando o status for 404', () => {
    const response = new Response(null, { status: 404 });
    const resultado = classificarRespostaFonte(response);

    expect(resultado).toEqual({ tipo: 'NOT_YET_PUBLISHED' });
  });

  it('retorna FONTE_INDISPONIVEL quando o status for 500', () => {
    const response = new Response(null, { status: 500 });
    const resultado = classificarRespostaFonte(response);

    expect(resultado).toEqual({ tipo: 'FONTE_INDISPONIVEL', detalhe: 'status 500' });
  });

  it('retorna FONTE_INDISPONIVEL quando o status for 503', () => {
    const response = new Response(null, { status: 503 });
    const resultado = classificarRespostaFonte(response);

    expect(resultado).toEqual({ tipo: 'FONTE_INDISPONIVEL', detalhe: 'status 503' });
  });

  it('retorna FONTE_INDISPONIVEL quando o status for 302 (status inesperado)', () => {
    const response = new Response(null, { status: 302 });
    const resultado = classificarRespostaFonte(response);

    expect(resultado).toEqual({ tipo: 'FONTE_INDISPONIVEL', detalhe: 'status inesperado 302' });
  });
});
