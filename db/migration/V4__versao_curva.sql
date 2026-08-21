-- ============================================================================
-- V4__versao_curva.sql
-- Criacao das tabelas de versoes de curva, vertices, procedencia e validacao.
-- ============================================================================

-- Tabela que armazena as versoes geradas e calculadas de cada curva.
CREATE TABLE versao_curva (
    id UNIQUEIDENTIFIER NOT NULL DEFAULT NEWSEQUENTIALID(),
    definicao_curva_id UNIQUEIDENTIFIER NOT NULL,
    versao_definicao_curva_id UNIQUEIDENTIFIER NOT NULL,
    data_referencia DATE NOT NULL,
    momento_curva NVARCHAR(20) NOT NULL,
    numero_versao INT NOT NULL,
    origem_versao NVARCHAR(20) NOT NULL,
    estado NVARCHAR(20) NOT NULL,
    execucao_curva_id UNIQUEIDENTIFIER NOT NULL,
    publicado_em DATETIME2 NULL,
    CONSTRAINT pk_versao_curva PRIMARY KEY (id),
    CONSTRAINT fk_versao_curva_definicao_curva FOREIGN KEY (definicao_curva_id) REFERENCES definicao_curva (id),
    CONSTRAINT fk_versao_curva_versao_definicao_curva FOREIGN KEY (versao_definicao_curva_id) REFERENCES versao_definicao_curva (id),
    CONSTRAINT fk_versao_curva_execucao_curva FOREIGN KEY (execucao_curva_id) REFERENCES execucao_curva (id),
    CONSTRAINT uq_versao_curva_definicao_data_momento_versao UNIQUE (definicao_curva_id, data_referencia, momento_curva, numero_versao),
    CONSTRAINT ck_versao_curva_momento_curva CHECK (momento_curva IN ('ABERTURA', 'INTRADIA', 'FECHAMENTO')),
    CONSTRAINT ck_versao_curva_origem_versao CHECK (origem_versao IN ('CALCULADA', 'IMPORTADA', 'CARREGADA')),
    CONSTRAINT ck_versao_curva_estado CHECK (estado IN ('EM_VALIDACAO', 'PUBLICADA', 'REPROVADA', 'SUBSTITUIDA'))
);

-- Indice unico filtrado que garante no maximo UMA versao com estado PUBLICADA por curva, data e momento,
-- impedindo que duas instancias do motor realizem publicacao concorrente em condicao de corrida.
CREATE UNIQUE INDEX ux_versao_curva_publicada
    ON versao_curva (definicao_curva_id, data_referencia, momento_curva)
    WHERE estado = 'PUBLICADA';

-- Tabela que armazena os pontos e taxas dos vertices calculados de uma versao da curva.
CREATE TABLE vertice_curva (
    id BIGINT IDENTITY(1,1) NOT NULL,
    versao_curva_id UNIQUEIDENTIFIER NOT NULL,
    prazo_dias_uteis INT NOT NULL,
    prazo_dias_corridos INT NULL,
    data_vencimento DATE NULL,
    taxa DECIMAL(28,12) NOT NULL,
    fator_desconto DECIMAL(28,12) NULL,
    CONSTRAINT pk_vertice_curva PRIMARY KEY (id),
    CONSTRAINT fk_vertice_curva_versao_curva FOREIGN KEY (versao_curva_id) REFERENCES versao_curva (id),
    CONSTRAINT uq_vertice_curva_versao_prazo_uteis UNIQUE (versao_curva_id, prazo_dias_uteis)
);

-- Tabela que registra a procedencia, auditoria e rastreabilidade dos insumos e modelo da versao da curva
-- (sem procedencia gravada na MESMA transacao, a versao nao chega a PUBLICADA).
CREATE TABLE procedencia_curva (
    id UNIQUEIDENTIFIER NOT NULL DEFAULT NEWSEQUENTIALID(),
    versao_curva_id UNIQUEIDENTIFIER NOT NULL,
    execucao_curva_id UNIQUEIDENTIFIER NOT NULL,
    numero_versao_definicao INT NOT NULL,
    checksum_modelo NVARCHAR(80) NULL,
    referencias_insumo NVARCHAR(MAX) NULL,
    hash_conjunto_insumos NVARCHAR(80) NULL,
    lote_ingestao_id BIGINT NULL,
    arquivo_carga NVARCHAR(400) NULL,
    hash_arquivo NVARCHAR(80) NULL,
    carregado_por NVARCHAR(100) NULL,
    justificativa NVARCHAR(MAX) NULL,
    versao_motor NVARCHAR(50) NULL,
    criado_em DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
    CONSTRAINT pk_procedencia_curva PRIMARY KEY (id),
    CONSTRAINT fk_procedencia_curva_versao_curva FOREIGN KEY (versao_curva_id) REFERENCES versao_curva (id),
    CONSTRAINT fk_procedencia_curva_execucao_curva FOREIGN KEY (execucao_curva_id) REFERENCES execucao_curva (id),
    CONSTRAINT fk_procedencia_curva_lote_ingestao FOREIGN KEY (lote_ingestao_id) REFERENCES lote_ingestao (id),
    CONSTRAINT uq_procedencia_curva_versao_curva UNIQUE (versao_curva_id)
);

-- Tabela que armazena os testes de validacao estatistica e financeira executados sobre a versao da curva.
-- O resultado e persistido tanto em aprovacao quanto em reprovacao para fins de auditoria detalhada.
CREATE TABLE validacao_curva (
    id BIGINT IDENTITY(1,1) NOT NULL,
    versao_curva_id UNIQUEIDENTIFIER NOT NULL,
    teste NVARCHAR(60) NOT NULL,
    classificacao NVARCHAR(20) NOT NULL,
    resultado NVARCHAR(20) NOT NULL,
    medida_observada DECIMAL(28,12) NULL,
    limite_aplicado DECIMAL(28,12) NULL,
    detalhe NVARCHAR(MAX) NULL,
    executado_em DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
    CONSTRAINT pk_validacao_curva PRIMARY KEY (id),
    CONSTRAINT fk_validacao_curva_versao_curva FOREIGN KEY (versao_curva_id) REFERENCES versao_curva (id),
    CONSTRAINT uq_validacao_curva_versao_teste UNIQUE (versao_curva_id, teste),
    CONSTRAINT ck_validacao_curva_classificacao CHECK (classificacao IN ('BLOQUEANTE', 'AVISO')),
    CONSTRAINT ck_validacao_curva_resultado CHECK (resultado IN ('APROVADO', 'REPROVADO', 'NAO_APLICAVEL'))
);
