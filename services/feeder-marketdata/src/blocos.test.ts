import { describe, expect, it } from 'vitest';
import { dividirEmBlocos } from './blocos.js';

describe('dividirEmBlocos', () => {
  it('retorna blocos com sobra no último bloco quando a divisão não é exata', () => {
    const resultado = dividirEmBlocos([1, 2, 3, 4, 5], 2);

    expect(resultado).toEqual([
      { sequencia: 1, totalBlocos: 3, registros: [1, 2] },
      { sequencia: 2, totalBlocos: 3, registros: [3, 4] },
      { sequencia: 3, totalBlocos: 3, registros: [5] },
    ]);
  });

  it('retorna blocos exatamente divididos quando a divisão é exata', () => {
    const resultado = dividirEmBlocos([1, 2, 3, 4], 2);

    expect(resultado).toEqual([
      { sequencia: 1, totalBlocos: 2, registros: [1, 2] },
      { sequencia: 2, totalBlocos: 2, registros: [3, 4] },
    ]);
  });

  it('retorna um único bloco quando tamanhoBloco é maior que a lista inteira', () => {
    const resultado = dividirEmBlocos([1, 2, 3], 10);

    expect(resultado).toEqual([{ sequencia: 1, totalBlocos: 1, registros: [1, 2, 3] }]);
  });

  it('lança erro quando a lista de registros for vazia', () => {
    expect(() => dividirEmBlocos([], 5)).toThrowError('registros não pode ser vazio');
  });

  it('lança erro quando tamanhoBloco for 0 ou negativo', () => {
    expect(() => dividirEmBlocos([1, 2, 3], 0)).toThrowError(
      'tamanhoBloco deve ser >= 1: recebido 0',
    );
    expect(() => dividirEmBlocos([1, 2, 3], -1)).toThrowError(
      'tamanhoBloco deve ser >= 1: recebido -1',
    );
  });
});
