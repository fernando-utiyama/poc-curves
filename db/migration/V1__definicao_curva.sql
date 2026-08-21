-- ============================================================================
-- V1__definicao_curva.sql
-- Criacao das tabelas de definicao e versionamento de configuracoes de curvas.
-- ============================================================================

-- Tabela que armazena o cadastro e definicao basica das curvas de mercado.
CREATE TABLE definicao_curva (
    id UNIQUEIDENTIFIER NOT NULL DEFAULT NEWSEQUENTIALID(),
    codigo NVARCHAR(50) NOT NULL,
    nome NVARCHAR(200) NOT NULL,
    moeda NVARCHAR(3) NOT NULL,
    modo_origem NVARCHAR(20) NOT NULL,
    horario_limite_publicacao TIME NOT NULL,
    estado NVARCHAR(20) NOT NULL,
    criado_em DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
    criado_por NVARCHAR(100) NOT NULL,
    CONSTRAINT pk_definicao_curva PRIMARY KEY (id),
    CONSTRAINT uq_definicao_curva_codigo UNIQUE (codigo),
    CONSTRAINT ck_definicao_curva_modo_origem CHECK (modo_origem IN ('BOOTSTRAPPED', 'IMPORTED')),
    CONSTRAINT ck_definicao_curva_estado CHECK (estado IN ('RASCUNHO', 'ATIVA', 'APOSENTADA'))
);

-- Tabela que armazena os parametros e configuracoes versionadas da definicao da curva.
CREATE TABLE versao_definicao_curva (
    id UNIQUEIDENTIFIER NOT NULL DEFAULT NEWSEQUENTIALID(),
    definicao_curva_id UNIQUEIDENTIFIER NOT NULL,
    numero_versao INT NOT NULL,
    contagem_dias NVARCHAR(20) NOT NULL,
    calendario NVARCHAR(50) NOT NULL,
    interpolador NVARCHAR(50) NOT NULL,
    politica_extrapolacao NVARCHAR(50) NOT NULL,
    politica_arredondamento NVARCHAR(50) NOT NULL,
    orcamento_ingestao_segundos INT NULL,
    orcamento_construcao_segundos INT NULL,
    orcamento_validacao_segundos INT NULL,
    orcamento_publicacao_segundos INT NULL,
    janela_bloqueio_minutos INT NULL,
    vinculos_fonte NVARCHAR(MAX) NULL,
    depende_de NVARCHAR(MAX) NULL,
    limites_validacao NVARCHAR(MAX) NULL,
    vigencia_inicio DATE NOT NULL,
    vigencia_fim DATE NULL,
    CONSTRAINT pk_versao_definicao_curva PRIMARY KEY (id),
    CONSTRAINT fk_versao_definicao_curva_definicao_curva FOREIGN KEY (definicao_curva_id) REFERENCES definicao_curva (id),
    CONSTRAINT uq_versao_definicao_curva_definicao_versao UNIQUE (definicao_curva_id, numero_versao)
);
