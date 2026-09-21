import { describe, expect, it } from 'vitest';
import type { Faixa } from './feeder.js';
import { resolverTopicoIngestao } from './faixa-topico.js';

describe('resolverTopicoIngestao', () => {
  it('retorna marketdata.rotina.v1 para faixa ROTINA', () => {
    expect(resolverTopicoIngestao('ROTINA')).toBe('marketdata.rotina.v1');
  });

  it('retorna marketdata.prioritaria.v1 para faixa PRIORITARIA', () => {
    expect(resolverTopicoIngestao('PRIORITARIA')).toBe('marketdata.prioritaria.v1');
  });

  it('retorna marketdata.massa.v1 para faixa MASSA', () => {
    expect(resolverTopicoIngestao('MASSA')).toBe('marketdata.massa.v1');
  });

  it('lança erro quando faixa é undefined ou null', () => {
    expect(() => resolverTopicoIngestao(undefined)).toThrowError(
      'faixa não pode ser vazia — todo disparo de aquisição precisa declarar a faixa de ingestão',
    );
    expect(() => resolverTopicoIngestao(null)).toThrowError(
      'faixa não pode ser vazia — todo disparo de aquisição precisa declarar a faixa de ingestão',
    );
  });

  it('lança erro quando faixa é desconhecida', () => {
    expect(() => resolverTopicoIngestao('QUALQUER_COISA' as Faixa)).toThrowError(
      'faixa desconhecida: QUALQUER_COISA',
    );
  });
});
