-- ============================================================================
-- V7__indices.sql
-- Criacao dos indices do caminho de consulta para otimizacao de performance.
-- ============================================================================

-- Indice para consulta de versoes de curva por definicao, data, momento e estado
CREATE NONCLUSTERED INDEX ix_versao_curva_vigente
    ON versao_curva (definicao_curva_id, data_referencia, momento_curva, estado);

-- NOTA: O indice ix_vertice_curva_prazo ON vertice_curva (versao_curva_id, prazo_dias_uteis)
-- nao e criado aqui por ser redundante: a constraint unique uq_vertice_curva_versao_prazo_uteis
-- ja criou um indice unico sobre exatamente as mesmas colunas (versao_curva_id, prazo_dias_uteis) em V4.

-- Indice para consulta de pontos de dados de mercado por fonte, conjunto e data
CREATE NONCLUSTERED INDEX ix_ponto_dado_mercado_dia
    ON ponto_dado_mercado (fonte, conjunto_dados, data_referencia);

-- Indice para rastreamento de execucoes de curva por correlacao
CREATE NONCLUSTERED INDEX ix_execucao_curva_correlacao
    ON execucao_curva (correlacao_id);

-- Indice para consulta de execucoes de curva por data de referencia e estado
CREATE NONCLUSTERED INDEX ix_execucao_curva_dia
    ON execucao_curva (data_referencia, estado);

-- Indice para busca de lotes de ingestao por fonte, conjunto e data de referencia
CREATE NONCLUSTERED INDEX ix_lote_ingestao_chave
    ON lote_ingestao (fonte, conjunto_dados, data_referencia);

-- Indice que sustenta a contagem de pendencias abertas agrupadas, que alimenta o alerta
CREATE NONCLUSTERED INDEX ix_pendencia_dlq_grupo
    ON pendencia_dlq (estado, motivo, fonte, conjunto_dados, data_referencia);

-- Indice para consulta das validacoes executadas para uma versao de curva
CREATE NONCLUSTERED INDEX ix_validacao_curva_versao
    ON validacao_curva (versao_curva_id);
