-- ============================================================================
-- V14__execucao_curva_indice_ativa_por_alvo.sql
-- Corrige um bug real encontrado ao verificar a tarefa 4.3/11.1 do backlog
-- curve-orchestrator (disparo manual de vários conjuntos de dados na mesma
-- acao): o indice unico ux_execucao_curva_ativa (V3__execucao_curva.sql)
-- cobre so (definicao_curva_id, data_referencia, momento_curva), sem
-- conjunto_dados. SQL Server trata NULL como igual a NULL num indice unico
-- (diferente do padrao ANSI/Postgres) -- confirmado ao vivo nesta sessao:
-- duas execucoes com conjunto_dados diferentes ('TESTE_A', 'TESTE_B') mas
-- definicao_curva_id NULO na mesma data/momento colidiram no indice antigo,
-- o que quebraria de verdade um disparo manual com mais de um conjunto de
-- dados na mesma data (o cenario real da tarefa 4.3).
--
-- Fix: dois indices filtrados separados, cada um exigindo que sua propria
-- coluna-alvo NAO seja nula -- fecha a ambiguidade de NULL sem duplicar a
-- checagem entre os dois "alvos" possiveis da entidade (definicao_curva_id
-- para carga manual por curva especifica, conjunto_dados para disparo
-- manual/agendado por conjunto de dados).
-- ============================================================================

DROP INDEX ux_execucao_curva_ativa ON execucao_curva;

CREATE UNIQUE INDEX ux_execucao_curva_ativa_definicao
    ON execucao_curva (definicao_curva_id, data_referencia, momento_curva)
    WHERE estado IN ('PENDENTE', 'EXECUTANDO', 'CONSTRUINDO') AND definicao_curva_id IS NOT NULL;

CREATE UNIQUE INDEX ux_execucao_curva_ativa_conjunto
    ON execucao_curva (conjunto_dados, data_referencia, momento_curva)
    WHERE estado IN ('PENDENTE', 'EXECUTANDO', 'CONSTRUINDO') AND conjunto_dados IS NOT NULL;
