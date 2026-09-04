import { describe, it, expect } from 'vitest';
import { ItemPainelDoDiaDTO } from '../core/api/models.ts';

describe('Painel do Dia — Cenários de Monitoramento de Curvas', () => {
  it('deve categorizar e identificar todos os estados operacionais possíveis no painel', () => {
    const itens: ItemPainelDoDiaDTO[] = [
      {
        codigoCurva: 'PRE',
        nomeCurva: 'Curva Pré Fixada DI1',
        modoOrigem: 'BOOTSTRAPPED',
        estado: 'PUBLICADA',
        horarioLimite: '19:00',
        tempoRestanteOuMargem: '+45 min',
        versaoVigente: 1
      },
      {
        codigoCurva: 'DOC',
        nomeCurva: 'Curva Cupom Cambial',
        modoOrigem: 'BOOTSTRAPPED',
        estado: 'EM_RISCO',
        horarioLimite: '18:30',
        tempoRestanteOuMargem: '08 min',
        etapaAtual: 'BOOTSTRAP_CURVA'
      },
      {
        codigoCurva: 'INP',
        nomeCurva: 'Curva Inflação Implícita',
        modoOrigem: 'BOOTSTRAPPED',
        estado: 'ATRASADA',
        horarioLimite: '17:00',
        tempoRestanteOuMargem: '-25 min',
        etapaAtual: 'INGESTAO_DADOS'
      },
      {
        codigoCurva: 'DPL',
        nomeCurva: 'Curva Dólar Ptax',
        modoOrigem: 'BOOTSTRAPPED',
        estado: 'REPROVADA',
        horarioLimite: '19:00',
        testesReprovados: ['MONOTONICIDADE_FATORES_DESCONTO', 'ARBITRAGEM_FORWARD'],
        versaoVigente: 2
      },
      {
        codigoCurva: 'CYI',
        nomeCurva: 'Curva Cupom Limpo',
        modoOrigem: 'BOOTSTRAPPED',
        estado: 'PUBLICADA_COM_AVISO',
        horarioLimite: '19:00',
        avisosValidacao: ['VARIACAO_SUPERIOR_100_BPS_D1'],
        versaoVigente: 1
      },
      {
        codigoCurva: 'PRE_B3',
        nomeCurva: 'Curva Pré Oficial B3',
        modoOrigem: 'IMPORTED',
        estado: 'NAO_INICIADA',
        horarioLimite: '19:30',
        horarioPrevisto: '18:45'
      }
    ];

    expect(itens.length).toBe(6);

    // Curva em risco antes de qualquer falha
    const emRisco = itens.find(i => i.codigoCurva === 'DOC');
    expect(emRisco?.estado).toBe('EM_RISCO');
    expect(emRisco?.tempoRestanteOuMargem).toBe('08 min');

    // Curva atrasada distinta de falha
    const atrasada = itens.find(i => i.codigoCurva === 'INP');
    expect(atrasada?.estado).toBe('ATRASADA');
    expect(atrasada?.tempoRestanteOuMargem).toContain('-');

    // Curva reprovada indicando testes que falharam e versão vigente anterior
    const reprovada = itens.find(i => i.codigoCurva === 'DPL');
    expect(reprovada?.estado).toBe('REPROVADA');
    expect(reprovada?.testesReprovados).toHaveLength(2);
    expect(reprovada?.versaoVigente).toBe(2);

    // Curva com aviso
    const comAviso = itens.find(i => i.codigoCurva === 'CYI');
    expect(comAviso?.estado).toBe('PUBLICADA_COM_AVISO');
    expect(comAviso?.avisosValidacao).toContain('VARIACAO_SUPERIOR_100_BPS_D1');

    // Curva não iniciada com horário previsto
    const naoIniciada = itens.find(i => i.codigoCurva === 'PRE_B3');
    expect(naoIniciada?.estado).toBe('NAO_INICIADA');
    expect(naoIniciada?.horarioPrevisto).toBe('18:45');
  });
});
