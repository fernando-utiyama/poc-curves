-- ============================================================================
-- V10__lote_ingestao_correlation_id.sql
-- Adiciona correlation_id em lote_ingestao (tarefa 4.7 do backlog do curve-processor):
-- a tabela nao tinha coluna para rastrear o correlationId do evento que abriu o lote.
-- ============================================================================

ALTER TABLE lote_ingestao
    ADD correlation_id UNIQUEIDENTIFIER NULL;
