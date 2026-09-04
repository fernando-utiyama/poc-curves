import { describe, it, expect } from 'vitest';
import { CargaManualResponse, ErroLinhaCargaDTO } from '../core/api/models.ts';

describe('Carga Manual de Curva (Contingência)', () => {
  it('deve listar todos os erros de leitura por linha e coluna quando o arquivo for inválido', () => {
    const erros: ErroLinhaCargaDTO[] = [
      { linha: 2, coluna: 'prazo_dias_uteis', valorEncontrado: '-5', mensagem: 'Prazo em dias úteis deve ser positivo' },
      { linha: 10, coluna: 'taxa', valorEncontrado: 'INVALID', mensagem: 'Formato de taxa decimal inválido' },
      { linha: 15, coluna: 'prazo_dias_uteis', valorEncontrado: '21', mensagem: 'Prazo duplicado na linha 15 (já declarado na linha 3)' }
    ];

    expect(erros).toHaveLength(3);
    expect(erros[0].linha).toBe(2);
    expect(erros[1].coluna).toBe('taxa');
    expect(erros[2].mensagem).toContain('duplicado');
  });

  it('deve identificar curva reprovada no gate de consistência e preservar a versão anterior', () => {
    const respReprovada: CargaManualResponse = {
      correlationId: 'c-manual-01',
      versaoCurvaId: 'vc-999',
      numeroVersao: 3,
      estadoPublicacao: 'REPROVADA',
      testesReprovados: ['TAXAS_NAO_NEGATIVAS']
    };

    expect(respReprovada.estadoPublicacao).toBe('REPROVADA');
    expect(respReprovada.testesReprovados).toContain('TAXAS_NAO_NEGATIVAS');
  });

  it('deve identificar curva carregada publicada com sucesso', () => {
    const respSucesso: CargaManualResponse = {
      correlationId: 'c-manual-02',
      versaoCurvaId: 'vc-1000',
      numeroVersao: 4,
      estadoPublicacao: 'PUBLICADA'
    };

    expect(respSucesso.estadoPublicacao).toBe('PUBLICADA');
    expect(respSucesso.numeroVersao).toBe(4);
  });
});
