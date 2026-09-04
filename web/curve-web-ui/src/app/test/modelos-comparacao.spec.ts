import { describe, it, expect } from 'vitest';
import { ComparacaoResponse } from '../core/api/models.ts';

describe('Modelos de Cálculo e Comparação de Curvas', () => {
  it('deve comparar duas curvas sinalizando prazos coincidentes e prazos exclusivos', () => {
    const resp: ComparacaoResponse = {
      dataReferencia: '2026-08-21',
      rotuloCurvaA: 'PRE (Construída DI1)',
      rotuloCurvaB: 'PRE_B3 (Oficial B3)',
      diferencas: [
        {
          prazoDiasUteis: 21,
          taxaA: '14.129000000000',
          taxaB: '14.129000000000',
          diferencaTaxaBps: '0.0000',
          status: 'COINCIDENTE'
        },
        {
          prazoDiasUteis: 42,
          taxaA: '14.200000000000',
          taxaB: '14.250000000000',
          diferencaTaxaBps: '-5.0000',
          status: 'COINCIDENTE'
        },
        {
          prazoDiasUteis: 63,
          taxaA: '14.300000000000',
          status: 'PRESENTE_APENAS_EM_A'
        },
        {
          prazoDiasUteis: 84,
          taxaB: '14.350000000000',
          status: 'PRESENTE_APENAS_EM_B'
        }
      ]
    };

    expect(resp.diferencas).toHaveLength(4);

    // Coincidente sem spread
    expect(resp.diferencas[0].status).toBe('COINCIDENTE');
    expect(resp.diferencas[0].diferencaTaxaBps).toBe('0.0000');

    // Coincidente com spread de -5 bps
    expect(resp.diferencas[1].diferencaTaxaBps).toBe('-5.0000');

    // Exclusivo em A (sem inventar taxa B)
    expect(resp.diferencas[2].status).toBe('PRESENTE_APENAS_EM_A');
    expect(resp.diferencas[2].taxaB).toBeUndefined();

    // Exclusivo em B (sem inventar taxa A)
    expect(resp.diferencas[3].status).toBe('PRESENTE_APENAS_EM_B');
    expect(resp.diferencas[3].taxaA).toBeUndefined();
  });
});
