-- ============================================================================
-- V17__curve_orchestrator_backfill.sql
-- Suporte a backfill (secao 7 do backlog curve-orchestrator). A execucao-mae
-- de um backfill (execucao_curva com disparo = 'BACKFILL', execucao_pai_id
-- IS NULL) cobre um intervalo de datas -- execucao_curva.data_referencia
-- guarda o inicio do intervalo -- e precisa de um sinal de interrupcao que
-- nao aborta filhas ja em andamento e de um limite de concorrencia.
--
-- Tabela de extensao em vez de alterar execucao_curva: mantem o schema e o
-- codigo (ExecucaoCurva, ExecucaoCurvaRepository) ja auditados e testados
-- neste backlog intocados -- so a execucao-mae de um backfill tem uma linha
-- aqui, as filhas (execucao_pai_id apontando pra ela) nao.
-- ============================================================================

CREATE TABLE backfill_execucao (
    execucao_curva_id UNIQUEIDENTIFIER NOT NULL,
    data_referencia_final DATE NOT NULL,
    concorrencia_maxima INT NOT NULL,
    interrupcao_solicitada BIT NOT NULL DEFAULT 0,
    CONSTRAINT pk_backfill_execucao PRIMARY KEY (execucao_curva_id),
    CONSTRAINT fk_backfill_execucao_execucao_curva FOREIGN KEY (execucao_curva_id) REFERENCES execucao_curva (id),
    CONSTRAINT ck_backfill_execucao_concorrencia_positiva CHECK (concorrencia_maxima > 0)
);

GRANT SELECT, INSERT, UPDATE ON backfill_execucao TO curve_orchestrator_app;
