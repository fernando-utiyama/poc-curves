-- ============================================================================
-- V5__modelo_curva.sql
-- Criacao da tabela modelo_curva e resolucao das dependencias circulares
-- via adicao de modelo_curva_id em versao_definicao_curva e procedencia_curva.
-- ============================================================================

-- Tabela que armazena o cadastro e definicao dos modelos matematicos e scripts de curva.
CREATE TABLE modelo_curva (
    id UNIQUEIDENTIFIER NOT NULL DEFAULT NEWSEQUENTIALID(),
    codigo NVARCHAR(80) NOT NULL,
    nome NVARCHAR(200) NOT NULL,
    tipo NVARCHAR(20) NOT NULL,
    estado NVARCHAR(20) NOT NULL,
    codigo_fonte NVARCHAR(MAX) NULL,
    checksum NVARCHAR(80) NULL,
    importado_por NVARCHAR(100) NULL,
    importado_em DATETIME2 NULL,
    CONSTRAINT pk_modelo_curva PRIMARY KEY (id),
    CONSTRAINT uq_modelo_curva_codigo UNIQUE (codigo),
    CONSTRAINT ck_modelo_curva_tipo CHECK (tipo IN ('BUILTIN', 'GROOVY')),
    CONSTRAINT ck_modelo_curva_estado CHECK (estado IN ('ATIVO', 'DESABILITADO'))
);
GO

-- Adiciona a coluna modelo_curva_id na tabela versao_definicao_curva
ALTER TABLE versao_definicao_curva ADD modelo_curva_id UNIQUEIDENTIFIER NULL;
GO

-- Adiciona a FK referenciando modelo_curva na tabela versao_definicao_curva
ALTER TABLE versao_definicao_curva ADD CONSTRAINT fk_versao_definicao_curva_modelo_curva
    FOREIGN KEY (modelo_curva_id) REFERENCES modelo_curva (id);
GO

-- Adiciona a coluna modelo_curva_id na tabela procedencia_curva
ALTER TABLE procedencia_curva ADD modelo_curva_id UNIQUEIDENTIFIER NULL;
GO

-- Adiciona a FK referenciando modelo_curva na tabela procedencia_curva
ALTER TABLE procedencia_curva ADD CONSTRAINT fk_procedencia_curva_modelo_curva
    FOREIGN KEY (modelo_curva_id) REFERENCES modelo_curva (id);
GO
