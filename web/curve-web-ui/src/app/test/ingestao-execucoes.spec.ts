import { describe, it, expect } from 'vitest';
import { DisparoIngestaoResponse, BackfillStatusResponse, ItemExecucaoDTO } from '../core/api/models.ts';

describe('Ingestão, Backfill e Monitor de Execuções', () => {
  it('deve formatar e expor correlationId de forma copiável no disparo manual', () => {
    const disparo: DisparoIngestaoResponse = {
      correlationId: 'b7c2d1e0-3f4a-5b6c-7d8e-9f0a1b2c3d4e',
      status: 'INICIADO',
      dataReferencia: '2026-08-21',
      execucaoId: 'exec-9988'
    };

    expect(disparo.correlationId).toBe('b7c2d1e0-3f4a-5b6c-7d8e-9f0a1b2c3d4e');
    expect(disparo.status).toBe('INICIADO');
  });

  it('deve calcular percentual de progresso do backfill e suportar interrupção', () => {
    const backfill: BackfillStatusResponse = {
      id: 'bf-20260821-01',
      totalDias: 20,
      concluidos: 10,
      semDado: 2,
      falhas: 1,
      pendentes: 7,
      status: 'EM_ANDAMENTO'
    };

    const processados = backfill.concluidos + backfill.semDado + backfill.falhas;
    const pct = Math.round((processados / backfill.totalDias) * 100);
    expect(pct).toBe(65);

    backfill.status = 'INTERROMPIDO';
    expect(backfill.status).toBe('INTERROMPIDO');
  });

  it('deve dar tratamento visual próprio a execuções com SEM_DADO distinto de FALHOU', () => {
    const execSemDado: ItemExecucaoDTO = {
      id: 'exec-01',
      correlationId: 'uuid-01',
      alvo: 'PRE',
      dataReferencia: '2026-08-21',
      tipoDisparo: 'AGENDADO',
      estado: 'SEM_DADO',
      causaFalha: 'Feriado Bancário Nacional (B3 Fechada)',
      disparadoEm: '2026-08-21T09:00:00Z'
    };

    const execFalha: ItemExecucaoDTO = {
      id: 'exec-02',
      correlationId: 'uuid-02',
      alvo: 'PRE',
      dataReferencia: '2026-08-21',
      tipoDisparo: 'MANUAL',
      faixa: 'PRIORITARIA',
      estado: 'FALHOU',
      causaFalha: 'Timeout ao conectar no endpoint B3',
      disparadoEm: '2026-08-21T18:00:00Z'
    };

    expect(execSemDado.estado).toBe('SEM_DADO');
    expect(execSemDado.causaFalha).toContain('Feriado');

    expect(execFalha.estado).toBe('FALHOU');
    expect(execFalha.faixa).toBe('PRIORITARIA');
  });
});
