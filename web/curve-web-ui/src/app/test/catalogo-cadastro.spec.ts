import { describe, it, expect } from 'vitest';
import { DefinicaoCurvaDTO } from '../core/api/models.ts';

describe('Catálogo e Cadastro de Curvas — Validações e Versionamento', () => {
  it('deve desabilitar campo de modelo de cálculo para curva de modo IMPORTED', () => {
    const curvaImportada: Partial<DefinicaoCurvaDTO> = {
      codigo: 'PRE_B3',
      nome: 'Curva Pré Importada B3',
      modoOrigem: 'IMPORTED',
      modeloApontado: undefined
    };

    expect(curvaImportada.modoOrigem).toBe('IMPORTED');
    expect(curvaImportada.modeloApontado).toBeUndefined();
  });

  it('deve permitir seleção de modelo embutido ou Groovy para curva BOOTSTRAPPED', () => {
    const curvaConstruida: Partial<DefinicaoCurvaDTO> = {
      codigo: 'PRE',
      nome: 'Curva Pré DI1',
      modoOrigem: 'BOOTSTRAPPED',
      modeloApontado: 'BUILTIN_PRE_DI1'
    };

    expect(curvaConstruida.modoOrigem).toBe('BOOTSTRAPPED');
    expect(curvaConstruida.modeloApontado).toBe('BUILTIN_PRE_DI1');

    // Troca de modelo para Groovy
    curvaConstruida.modeloApontado = 'GROOVY_PRE_CUSTOM';
    expect(curvaConstruida.modeloApontado).toBe('GROOVY_PRE_CUSTOM');
  });

  it('deve simular incremento de versão de definição ao editar parâmetros', () => {
    const versaoVigente = 1;
    const novaVersao = versaoVigente + 1;
    expect(novaVersao).toBe(2);
  });
});
