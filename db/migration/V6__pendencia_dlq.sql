-- ============================================================================
-- V6__pendencia_dlq.sql
-- Criacao da tabela de pendencias da dead-letter queue (DLQ) para reprocessamento.
-- ============================================================================

-- Tabela que armazena o controle e coordenadas de mensagens na dead-letter queue (DLQ)
-- para fins de reprocessamento e auditoria (guarda as coordenadas da mensagem, nunca o payload).
CREATE TABLE pendencia_dlq (
    id BIGINT IDENTITY(1,1) NOT NULL,
    id_evento NVARCHAR(100) NOT NULL,
    correlacao_id UNIQUEIDENTIFIER NULL,
    motivo NVARCHAR(60) NOT NULL,
    detalhe NVARCHAR(MAX) NULL,
    fonte NVARCHAR(20) NULL,
    conjunto_dados NVARCHAR(50) NULL,
    data_referencia DATE NULL,
    topico_origem NVARCHAR(120) NOT NULL,
    particao_origem INT NULL,
    offset_origem BIGINT NULL,
    topico_dlq NVARCHAR(160) NOT NULL,
    particao_dlq INT NULL,
    offset_dlq BIGINT NULL,
    grupo_consumo NVARCHAR(80) NULL,
    versao_aplicacao NVARCHAR(50) NULL,
    falhou_em DATETIME2 NOT NULL,
    tentativas INT NOT NULL DEFAULT 1,
    estado NVARCHAR(25) NOT NULL,
    desfecho_em DATETIME2 NULL,
    responsavel NVARCHAR(100) NULL,
    justificativa NVARCHAR(MAX) NULL,
    CONSTRAINT pk_pendencia_dlq PRIMARY KEY (id),
    CONSTRAINT uq_pendencia_dlq_id_evento UNIQUE (id_evento),
    CONSTRAINT ck_pendencia_dlq_estado CHECK (estado IN ('ABERTA', 'EM_REPROCESSAMENTO', 'RESOLVIDA', 'DESCARTADA', 'OBSOLETA'))
);
