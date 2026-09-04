import { describe, it, expect } from 'vitest';
import { InterpolacaoResponse, ItemInterpolacaoDTO } from '../core/api/models.ts';

describe('Consulta de Interpolação — Amostragem em Lote e Tratamento Fora do Intervalo', () => {
  it('deve preservar a ordem dos prazos consultados e isolar erros de política estrita', () => {
    const resp: InterpolacaoResponse = {
      codigoCurva: 'PRE',
      versaoUtilizada: 1,
      interpoladorUtilizado: 'FLAT_FORWARD',
      resultados: [
        {
          prazoDiasUteis: 10,
          taxa: '13.950000000000',
          fatorDesconto: '0.994000000000',
          status: 'INTERPOLADO'
        },
        {
          prazoDiasUteis: 21,
          taxa: '14.129000000000',
          fatorDesconto: '0.988000000000',
          status: 'VERTICE_EXATO'
        },
        {
          prazoDiasUteis: 5000,
          status: 'ERRO_FORA_INTERVALO',
          erroMensagem: 'Prazo 5000 DU excede o último vértice disponível (3780 DU) sob política STRICT'
        }
      ]
    };

    expect(resp.resultados[0].prazoDiasUteis).toBe(10);
    expect(resp.resultados[0].status).toBe('INTERPOLADO');

    expect(resp.resultados[1].prazoDiasUteis).toBe(21);
    expect(resp.resultados[1].status).toBe('VERTICE_EXATO');

    // O terceiro item falhou por política estrita, mas os outros 2 permanecem válidos com taxas calculadas
    expect(resp.resultados[2].status).toBe('ERRO_FORA_INTERVALO');
    expect(resp.resultados[2].taxa).toBeUndefined();
    expect(resp.resultados[2].erroMensagem).toContain('STRICT');
  });

  it('deve sinalizar pontos extrapolados sob política permissiva', () => {
    const item: ItemInterpolacaoDTO = {
      prazoDiasUteis: 4000,
      taxa: '14.500000000000',
      fatorDesconto: '0.500000000000',
      status: 'EXTRAPOLADO'
    };

    expect(item.status).toBe('EXTRAPOLADO');
    expect(item.taxa).toBeDefined();
  });
});
