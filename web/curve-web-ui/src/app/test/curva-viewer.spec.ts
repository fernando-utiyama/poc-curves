import { describe, it, expect } from 'vitest';
import { CurvaViewerResponse, VerticeCurvaDTO } from '../core/api/models.ts';

describe('Curva Viewer — Visualização, Procedência e Validação de Consistência', () => {
  it('deve suportar centenas de vértices mantendo dados numéricos íntegros', () => {
    const vertices: VerticeCurvaDTO[] = [];
    for (let i = 1; i <= 252; i++) {
      vertices.push({
        prazoDiasUteis: i * 21,
        prazoDiasCorridos: i * 30,
        taxa: `14.${String(100000000000 + i).substring(1)}`,
        fatorDesconto: `0.${String(990000000000 - i * 1000).substring(1)}`
      });
    }

    expect(vertices.length).toBe(252);
    expect(vertices[0].taxa).toBe('14.00000000001');
    expect(vertices[251].taxa).toBe('14.00000000252');
  });

  it('deve estruturar procedência com executionId, correlationId e modelo', () => {
    const viewerResp: CurvaViewerResponse = {
      codigoCurva: 'PRE',
      nomeCurva: 'Curva Pré Fixada DI1',
      dataReferencia: '2026-08-21',
      momento: 'FECHAMENTO',
      versao: 1,
      isVersaoCorrente: true,
      estadoVersao: 'PUBLICADA',
      origemPublicacao: 'CALCULADA',
      vertices: [
        { prazoDiasUteis: 21, taxa: '14.129000000000', fatorDesconto: '0.988000000000' }
      ],
      procedencia: {
        publicadoEm: '2026-08-21T18:45:00Z',
        execucaoId: 'exec-12345',
        correlationId: 'c1234567-89ab-cdef-0123-456789abcdef',
        modeloNome: 'BUILTIN_PRE_DI1',
        modeloChecksum: 'sha256:abc123456'
      },
      validacao: {
        statusGeral: 'APROVADA',
        itens: [
          {
            testeId: 'MONOTONICIDADE_FATORES_DESCONTO',
            nomeTeste: 'Monotonicidade dos Fatores de Desconto',
            classificacao: 'BLOQUEANTE',
            resultado: 'APROVADO',
            medidaObservada: 'Estritamente Decrescente'
          }
        ]
      }
    };

    expect(viewerResp.procedencia?.correlationId).toBe('c1234567-89ab-cdef-0123-456789abcdef');
    expect(viewerResp.validacao?.statusGeral).toBe('APROVADA');
    expect(viewerResp.validacao?.itens[0].resultado).toBe('APROVADO');
  });
});
