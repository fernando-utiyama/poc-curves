-- ============================================================================
-- V9__curve_processor_validacao_curva.sql
-- Extensao da fronteira de escrita do curve-processor: validacao_curva
-- (bateria de validacao da carga manual, tarefas 6.7/6.8 do backlog do servico).
-- ============================================================================

GRANT SELECT, INSERT ON validacao_curva TO curve_processor_app;
