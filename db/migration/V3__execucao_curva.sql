-- ============================================================================
-- V3__execucao_curva.sql
-- Criacao da tabela de execucao de curvas, resolucao de FK em lote_ingestao
-- e indice unico filtrado para execucoes concorrentes ativas.
-- ============================================================================

-- Tabela que armazena o ciclo de vida e execucoes de construcao e calculo de curvas.
CREATE TABLE execucao_curva (
    id UNIQUEIDENTIFIER NOT NULL DEFAULT NEWSEQUENTIALID(),
    correlacao_id UNIQUEIDENTIFIER NOT NULL,
    execucao_pai_id UNIQUEIDENTIFIER NULL,
    definicao_curva_id UNIQUEIDENTIFIER NULL,
    conjunto_dados NVARCHAR(50) NULL,
    data_referencia DATE NULL,
    momento_curva NVARCHAR(20) NULL,
    disparo NVARCHAR(20) NOT NULL,
    disparado_por NVARCHAR(100) NULL,
    faixa NVARCHAR(20) NOT NULL,
    estado NVARCHAR(20) NOT NULL,
    motivo_sem_dado NVARCHAR(50) NULL,
    horario_limite DATETIME2 NULL,
    margem_segundos INT NULL,
    duracao_por_etapa NVARCHAR(MAX) NULL,
    tentativas INT NOT NULL DEFAULT 0,
    codigo_erro NVARCHAR(50) NULL,
    mensagem_erro NVARCHAR(MAX) NULL,
    iniciado_em DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
    finalizado_em DATETIME2 NULL,
    CONSTRAINT pk_execucao_curva PRIMARY KEY (id),
    CONSTRAINT fk_execucao_curva_execucao_pai FOREIGN KEY (execucao_pai_id) REFERENCES execucao_curva (id),
    CONSTRAINT fk_execucao_curva_definicao_curva FOREIGN KEY (definicao_curva_id) REFERENCES definicao_curva (id),
    CONSTRAINT ck_execucao_curva_momento_curva CHECK (momento_curva IN ('ABERTURA', 'INTRADIA', 'FECHAMENTO')),
    CONSTRAINT ck_execucao_curva_disparo CHECK (disparo IN ('AGENDADO', 'MANUAL', 'BACKFILL', 'CARGA_MANUAL')),
    CONSTRAINT ck_execucao_curva_faixa CHECK (faixa IN ('ROTINA', 'PRIORITARIA', 'MASSA')),
    CONSTRAINT ck_execucao_curva_estado CHECK (estado IN ('PENDENTE', 'EXECUTANDO', 'CONSTRUINDO', 'EM_RISCO', 'ATRASADA', 'CONCLUIDA', 'SEM_DADO', 'FALHOU'))
);

-- Adiciona a chave estrangeira em lote_ingestao referenciando execucao_curva
ALTER TABLE lote_ingestao ADD CONSTRAINT fk_lote_ingestao_execucao_curva
    FOREIGN KEY (execucao_curva_id) REFERENCES execucao_curva (id);

-- Este indice garante, em cenarios com multiplas instancias do servico,
-- que dois acionamentos simultaneos nao criem duas execucoes ativas concorrentes.
CREATE UNIQUE INDEX ux_execucao_curva_ativa
    ON execucao_curva (definicao_curva_id, data_referencia, momento_curva)
    WHERE estado IN ('PENDENTE', 'EXECUTANDO', 'CONSTRUINDO');
