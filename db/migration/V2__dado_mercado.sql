-- ============================================================================
-- V2__dado_mercado.sql
-- Criacao das tabelas de ingestao e pontos de dados de mercado.
-- ============================================================================

-- Tabela que armazena os lotes de ingestao de dados de mercado recebidos.
CREATE TABLE lote_ingestao (
    id BIGINT IDENTITY(1,1) NOT NULL,
    execucao_curva_id UNIQUEIDENTIFIER NULL, -- FK adicionada posteriormente em V3
    fonte NVARCHAR(20) NOT NULL,
    conjunto_dados NVARCHAR(50) NOT NULL,
    tipo_payload NVARCHAR(20) NOT NULL,
    data_referencia DATE NOT NULL,
    lote_externo_id NVARCHAR(100) NOT NULL,
    id_evento NVARCHAR(100) NOT NULL,
    hash_payload NVARCHAR(80) NOT NULL,
    total_blocos INT NOT NULL,
    blocos_recebidos INT NOT NULL DEFAULT 0,
    pontos_recebidos INT NOT NULL DEFAULT 0,
    pontos_gravados INT NOT NULL DEFAULT 0,
    divergencias NVARCHAR(MAX) NULL,
    estado NVARCHAR(20) NOT NULL,
    recebido_em DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
    CONSTRAINT pk_lote_ingestao PRIMARY KEY (id),
    CONSTRAINT uq_lote_ingestao_lote_externo_id UNIQUE (lote_externo_id),
    CONSTRAINT ck_lote_ingestao_tipo_payload CHECK (tipo_payload IN ('INDIVIDUAL_QUOTES', 'READY_CURVE')),
    CONSTRAINT ck_lote_ingestao_estado CHECK (estado IN ('ABERTO', 'COMPLETO', 'INCOMPLETO'))
);

-- Tabela que armazena os pontos individuais de dados de mercado ingeridos.
CREATE TABLE ponto_dado_mercado (
    id BIGINT IDENTITY(1,1) NOT NULL,
    fonte NVARCHAR(20) NOT NULL,
    conjunto_dados NVARCHAR(50) NOT NULL,
    data_referencia DATE NOT NULL,
    chave_instrumento NVARCHAR(100) NOT NULL,
    valor DECIMAL(28,12) NOT NULL,
    tipo_cotacao NVARCHAR(30) NULL,
    data_vencimento DATE NULL,
    lote_ingestao_id BIGINT NOT NULL,
    atualizado_em DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
    CONSTRAINT pk_ponto_dado_mercado PRIMARY KEY (id),
    CONSTRAINT fk_ponto_dado_mercado_lote_ingestao FOREIGN KEY (lote_ingestao_id) REFERENCES lote_ingestao (id),
    CONSTRAINT uq_ponto_dado_mercado_fonte_conjunto_data_chave UNIQUE (fonte, conjunto_dados, data_referencia, chave_instrumento)
);
