import { describe, expect, it } from 'vitest';
import { calcularEventId, calcularLoteId } from './lote-id.js';

const UUID_REGEX = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

describe('calcularLoteId', () => {
  const source = 'b3';
  const dataset = 'marketdata';
  const referenceDate = '2026-08-21';
  const conteudo = 'conteudo-de-teste-1';

  it('produz o mesmo valor quando chamado duas vezes com os mesmos argumentos', () => {
    const loteId1 = calcularLoteId(source, dataset, referenceDate, conteudo);
    const loteId2 = calcularLoteId(source, dataset, referenceDate, conteudo);

    expect(loteId1).toBe(loteId2);
  });

  it('produz valor diferente quando o conteúdo é diferente', () => {
    const loteIdOriginal = calcularLoteId(source, dataset, referenceDate, conteudo);
    const loteIdOutroConteudo = calcularLoteId(
      source,
      dataset,
      referenceDate,
      'conteudo-de-teste-2',
    );

    expect(loteIdOutroConteudo).not.toBe(loteIdOriginal);
  });

  it('produz valor diferente quando referenceDate é diferente', () => {
    const loteIdOriginal = calcularLoteId(source, dataset, referenceDate, conteudo);
    const loteIdOutraData = calcularLoteId(source, dataset, '2026-08-22', conteudo);

    expect(loteIdOutraData).not.toBe(loteIdOriginal);
  });

  it('retorna um valor no formato UUID válido', () => {
    const loteId = calcularLoteId(source, dataset, referenceDate, conteudo);

    expect(loteId).toMatch(UUID_REGEX);
  });
});

describe('calcularEventId', () => {
  const loteId = '2a973c65-6096-4a59-bda3-c91bb9947d6f';
  const sequencia = 1;

  it('produz o mesmo valor quando chamado duas vezes com o mesmo loteId e sequencia', () => {
    const eventId1 = calcularEventId(loteId, sequencia);
    const eventId2 = calcularEventId(loteId, sequencia);

    expect(eventId1).toBe(eventId2);
  });

  it('produz valor diferente quando a sequencia é diferente', () => {
    const eventIdOriginal = calcularEventId(loteId, sequencia);
    const eventIdOutraSequencia = calcularEventId(loteId, 2);

    expect(eventIdOutraSequencia).not.toBe(eventIdOriginal);
  });

  it('produz valor diferente quando o loteId é diferente', () => {
    const eventIdOriginal = calcularEventId(loteId, sequencia);
    const eventIdOutroLoteId = calcularEventId('3b973c65-6096-4a59-bda3-c91bb9947d6e', sequencia);

    expect(eventIdOutroLoteId).not.toBe(eventIdOriginal);
  });

  it('retorna um valor no formato UUID válido', () => {
    const eventId = calcularEventId(loteId, sequencia);

    expect(eventId).toMatch(UUID_REGEX);
  });
});
