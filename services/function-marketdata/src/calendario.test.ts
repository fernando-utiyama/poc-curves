import { describe, expect, it } from 'vitest';
import { ehDiaDePregao, feriadosDoAno } from './calendario.js';

describe('feriadosDoAno', () => {
  it('contém os feriados fixos de 2026', () => {
    const feriados = feriadosDoAno(2026);

    expect(feriados.has('2026-01-01')).toBe(true); // Confraternização Universal
    expect(feriados.has('2026-04-21')).toBe(true); // Tiradentes
    expect(feriados.has('2026-05-01')).toBe(true); // Dia do Trabalho
    expect(feriados.has('2026-09-07')).toBe(true); // Independência
    expect(feriados.has('2026-10-12')).toBe(true); // N. Sra. Aparecida
    expect(feriados.has('2026-11-02')).toBe(true); // Finados
    expect(feriados.has('2026-11-15')).toBe(true); // Proclamação da República
    expect(feriados.has('2026-11-20')).toBe(true); // Consciência Negra
    expect(feriados.has('2026-12-25')).toBe(true); // Natal
  });

  it('contém os feriados móveis de 2026, conferidos contra o calendário oficial ANBIMA', () => {
    const feriados = feriadosDoAno(2026);

    expect(feriados.has('2026-02-16')).toBe(true); // Carnaval — segunda
    expect(feriados.has('2026-02-17')).toBe(true); // Carnaval — terça
    expect(feriados.has('2026-04-03')).toBe(true); // Sexta-feira Santa
    expect(feriados.has('2026-06-04')).toBe(true); // Corpus Christi
  });

  it('NÃO contém quinta-feira santa (dia útil desde a Resolução ANBIMA nº 2.516)', () => {
    const feriados = feriadosDoAno(2026);

    expect(feriados.has('2026-04-02')).toBe(false);
  });

  it('calcula corretamente os feriados móveis de outro ano (2027)', () => {
    // Páscoa 2027 = 28 de março (conferido de forma independente pelo algoritmo).
    const feriados = feriadosDoAno(2027);

    expect(feriados.has('2027-02-08')).toBe(true); // Carnaval — segunda (Páscoa -48)
    expect(feriados.has('2027-02-09')).toBe(true); // Carnaval — terça (Páscoa -47)
    expect(feriados.has('2027-03-26')).toBe(true); // Sexta-feira Santa (Páscoa -2)
    expect(feriados.has('2027-05-27')).toBe(true); // Corpus Christi (Páscoa +60)
  });
});

describe('ehDiaDePregao', () => {
  it('retorna false para feriado nacional (2026-01-01, quinta-feira)', () => {
    expect(ehDiaDePregao('2026-01-01')).toBe(false);
  });

  it('retorna true para dia útil comum (2026-01-02, sexta-feira, não feriado)', () => {
    expect(ehDiaDePregao('2026-01-02')).toBe(true);
  });

  it('retorna false para sábado (2026-01-03)', () => {
    expect(ehDiaDePregao('2026-01-03')).toBe(false);
  });

  it('retorna false para domingo (2026-01-04)', () => {
    expect(ehDiaDePregao('2026-01-04')).toBe(false);
  });

  it('retorna true para quinta-feira santa (2026-04-02, dia útil por resolução ANBIMA)', () => {
    expect(ehDiaDePregao('2026-04-02')).toBe(true);
  });

  it('lança erro para data inválida', () => {
    expect(() => ehDiaDePregao('data-invalida')).toThrow();
  });

  it('lança erro para ano fora do intervalo suportado', () => {
    expect(() => ehDiaDePregao('2050-01-01')).toThrow(/fora do intervalo/);
    expect(() => ehDiaDePregao('2010-01-01')).toThrow(/fora do intervalo/);
  });
});
