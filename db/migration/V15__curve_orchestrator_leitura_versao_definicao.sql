-- ============================================================================
-- V15__curve_orchestrator_leitura_versao_definicao.sql
-- Concede leitura de versao_definicao_curva ao curve_orchestrator_app --
-- necessario para a tarefa 8.1 do backlog (resolucao das definicoes de
-- curva que consomem um conjunto de dados, via vinculos_fonte da versao
-- vigente). V13 ja concedeu SELECT em definicao_curva, mas nao na tabela
-- de versoes -- gap encontrado ao planejar o encadeamento ate a construcao.
-- ============================================================================

GRANT SELECT ON versao_definicao_curva TO curve_orchestrator_app;
