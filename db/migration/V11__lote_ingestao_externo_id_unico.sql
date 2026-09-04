-- ============================================================================
-- V11__lote_ingestao_externo_id_unico.sql
-- Adiciona restricao UNIQUE em lote_ingestao.lote_externo_id (tarefa 8.19 do
-- backlog do curve-processor): sem ela, duas faixas de consumo (ex. rotina e
-- prioritaria) recebendo o MESMO lote concorrentemente podem ambas encontrar
-- "nao existe ainda" na leitura e ambas tentarem INSERT, criando duas linhas
-- para o mesmo lote_externo_id. O codigo (IngestaoService.processarBloco) ja
-- trata a violacao desta restricao como sinal de corrida perdida, recarregando
-- o lote vencedor em vez de duplicar.
-- ============================================================================

CREATE UNIQUE INDEX UX_lote_ingestao_lote_externo_id
    ON lote_ingestao (lote_externo_id);
