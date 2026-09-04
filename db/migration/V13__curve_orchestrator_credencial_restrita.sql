-- ============================================================================
-- V13__curve_orchestrator_credencial_restrita.sql
-- Credencial restrita do curve-orchestrator (tarefa 1.2 do backlog).
--
-- Nota: o GRANT sobre a tabela de agendamentos sera adicionado quando ela for
-- criada pela migracao futura da tarefa 6.1 (ainda nao existe hoje).
-- ============================================================================

CREATE LOGIN curve_orchestrator_app WITH PASSWORD = 'CurveOrchestratorP0c!Local', CHECK_POLICY = OFF;
CREATE USER curve_orchestrator_app FOR LOGIN curve_orchestrator_app;

-- Fronteira de escrita do servico: ciclo de vida das execucoes de curva
-- (ExecucaoCurvaRepository) e gestao de pendencias de dead-letter
-- (PendenciaDlqRepository, grupo 10 do backlog).
GRANT SELECT, INSERT, UPDATE ON execucao_curva TO curve_orchestrator_app;
GRANT SELECT, INSERT, UPDATE ON pendencia_dlq TO curve_orchestrator_app;

-- Fronteira de leitura: resolucao de definicao de curva (horario_limite_publicacao,
-- modo_origem) — sem permissao de escrita, definicao_curva pertence ao curve-api.
GRANT SELECT ON definicao_curva TO curve_orchestrator_app;
