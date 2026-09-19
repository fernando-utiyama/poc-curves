-- ============================================================================
-- db/h2/schema.sql
-- Esquema unificado para H2 compartilhado (Modo Servidor TCP)
-- Compativel com as entidades e operacoes JDBC da Plataforma de Curvas.
-- ============================================================================

-- Usuarios de aplicacao
CREATE USER IF NOT EXISTS curve_processor_app PASSWORD 'CurveProcessorP0c!Local' ADMIN;
CREATE USER IF NOT EXISTS curve_api_app PASSWORD 'CurveApiP0c!Local' ADMIN;
CREATE USER IF NOT EXISTS curve_engine_app PASSWORD 'CurveEngineP0c!Local' ADMIN;
CREATE USER IF NOT EXISTS curve_orchestrator_app PASSWORD 'CurveOrchestratorP0c!Local' ADMIN;

-- 1. definicao_curva (V1, V20)
CREATE TABLE IF NOT EXISTS definicao_curva (
    id UUID NOT NULL DEFAULT RANDOM_UUID(),
    codigo VARCHAR(50) NOT NULL,
    nome VARCHAR(200) NOT NULL,
    moeda VARCHAR(3) NOT NULL,
    modo_origem VARCHAR(20) NOT NULL,
    horario_limite_publicacao TIME NOT NULL,
    estado VARCHAR(20) NOT NULL,
    criado_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    criado_por VARCHAR(100) NOT NULL,
    codigo_curva_importada_irmao VARCHAR(50) NULL,
    CONSTRAINT pk_definicao_curva PRIMARY KEY (id),
    CONSTRAINT uq_definicao_curva_codigo UNIQUE (codigo),
    CONSTRAINT ck_definicao_curva_modo_origem CHECK (modo_origem IN ('BOOTSTRAPPED', 'IMPORTED')),
    CONSTRAINT ck_definicao_curva_estado CHECK (estado IN ('RASCUNHO', 'ATIVA', 'APOSENTADA'))
);

-- 2. modelo_curva (V5)
CREATE TABLE IF NOT EXISTS modelo_curva (
    id UUID NOT NULL DEFAULT RANDOM_UUID(),
    codigo VARCHAR(80) NOT NULL,
    nome VARCHAR(200) NOT NULL,
    tipo VARCHAR(20) NOT NULL,
    estado VARCHAR(20) NOT NULL,
    codigo_fonte CLOB NULL,
    checksum VARCHAR(80) NULL,
    importado_por VARCHAR(100) NULL,
    importado_em TIMESTAMP NULL,
    CONSTRAINT pk_modelo_curva PRIMARY KEY (id),
    CONSTRAINT uq_modelo_curva_codigo UNIQUE (codigo),
    CONSTRAINT ck_modelo_curva_tipo CHECK (tipo IN ('BUILTIN', 'GROOVY')),
    CONSTRAINT ck_modelo_curva_estado CHECK (estado IN ('ATIVO', 'DESABILITADO'))
);

-- 3. versao_definicao_curva (V1, V5)
CREATE TABLE IF NOT EXISTS versao_definicao_curva (
    id UUID NOT NULL DEFAULT RANDOM_UUID(),
    definicao_curva_id UUID NOT NULL,
    numero_versao INT NOT NULL,
    contagem_dias VARCHAR(20) NOT NULL,
    calendario VARCHAR(50) NOT NULL,
    interpolador VARCHAR(50) NOT NULL,
    politica_extrapolacao VARCHAR(50) NOT NULL,
    politica_arredondamento VARCHAR(50) NOT NULL,
    orcamento_ingestao_segundos INT NULL,
    orcamento_construcao_segundos INT NULL,
    orcamento_validacao_segundos INT NULL,
    orcamento_publicacao_segundos INT NULL,
    janela_bloqueio_minutos INT NULL,
    vinculos_fonte CLOB NULL,
    depende_de CLOB NULL,
    limites_validacao CLOB NULL,
    vigencia_inicio DATE NOT NULL,
    vigencia_fim DATE NULL,
    modelo_curva_id UUID NULL,
    CONSTRAINT pk_versao_definicao_curva PRIMARY KEY (id),
    CONSTRAINT fk_versao_definicao_curva_definicao_curva FOREIGN KEY (definicao_curva_id) REFERENCES definicao_curva (id),
    CONSTRAINT fk_versao_definicao_curva_modelo_curva FOREIGN KEY (modelo_curva_id) REFERENCES modelo_curva (id),
    CONSTRAINT uq_versao_definicao_curva_definicao_versao UNIQUE (definicao_curva_id, numero_versao)
);

-- 4. agendamento_curva (V16)
CREATE TABLE IF NOT EXISTS agendamento_curva (
    id UUID NOT NULL DEFAULT RANDOM_UUID(),
    definicao_curva_id UUID NULL,
    conjunto_dados VARCHAR(50) NULL,
    momento_curva VARCHAR(20) NOT NULL,
    faixa VARCHAR(20) NOT NULL,
    expressao_horario VARCHAR(100) NOT NULL,
    fuso_horario VARCHAR(50) NOT NULL,
    janela_tentativa_minutos INT NOT NULL,
    intervalo_tentativa_segundos INT NOT NULL,
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    criado_por VARCHAR(100) NOT NULL,
    criado_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    atualizado_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_agendamento_curva PRIMARY KEY (id),
    CONSTRAINT fk_agendamento_curva_definicao_curva FOREIGN KEY (definicao_curva_id) REFERENCES definicao_curva (id),
    CONSTRAINT ck_agendamento_curva_momento_curva CHECK (momento_curva IN ('ABERTURA', 'INTRADIA', 'FECHAMENTO')),
    CONSTRAINT ck_agendamento_curva_faixa CHECK (faixa IN ('ROTINA', 'PRIORITARIA', 'MASSA')),
    CONSTRAINT ck_agendamento_curva_janela_positiva CHECK (janela_tentativa_minutos > 0),
    CONSTRAINT ck_agendamento_curva_intervalo_positivo CHECK (intervalo_tentativa_segundos > 0)
);

-- 5. execucao_curva (V3, V14, V16)
CREATE TABLE IF NOT EXISTS execucao_curva (
    id UUID NOT NULL DEFAULT RANDOM_UUID(),
    correlacao_id UUID NOT NULL,
    execucao_pai_id UUID NULL,
    definicao_curva_id UUID NULL,
    conjunto_dados VARCHAR(50) NULL,
    data_referencia DATE NULL,
    momento_curva VARCHAR(20) NULL,
    disparo VARCHAR(20) NOT NULL,
    disparado_por VARCHAR(100) NULL,
    faixa VARCHAR(20) NOT NULL,
    estado VARCHAR(20) NOT NULL,
    motivo_sem_dado VARCHAR(50) NULL,
    horario_limite TIMESTAMP NULL,
    margem_segundos INT NULL,
    duracao_por_etapa CLOB NULL,
    tentativas INT NOT NULL DEFAULT 0,
    codigo_erro VARCHAR(50) NULL,
    mensagem_erro CLOB NULL,
    iniciado_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    finalizado_em TIMESTAMP NULL,
    agendamento_id UUID NULL,
    CONSTRAINT pk_execucao_curva PRIMARY KEY (id),
    CONSTRAINT fk_execucao_curva_execucao_pai FOREIGN KEY (execucao_pai_id) REFERENCES execucao_curva (id),
    CONSTRAINT fk_execucao_curva_definicao_curva FOREIGN KEY (definicao_curva_id) REFERENCES definicao_curva (id),
    CONSTRAINT fk_execucao_curva_agendamento FOREIGN KEY (agendamento_id) REFERENCES agendamento_curva (id),
    CONSTRAINT ck_execucao_curva_momento_curva CHECK (momento_curva IN ('ABERTURA', 'INTRADIA', 'FECHAMENTO')),
    CONSTRAINT ck_execucao_curva_disparo CHECK (disparo IN ('AGENDADO', 'MANUAL', 'BACKFILL', 'CARGA_MANUAL')),
    CONSTRAINT ck_execucao_curva_faixa CHECK (faixa IN ('ROTINA', 'PRIORITARIA', 'MASSA')),
    CONSTRAINT ck_execucao_curva_estado CHECK (estado IN ('PENDENTE', 'EXECUTANDO', 'CONSTRUINDO', 'EM_RISCO', 'ATRASADA', 'CONCLUIDA', 'SEM_DADO', 'FALHOU'))
);

-- 6. backfill_execucao (V17)
CREATE TABLE IF NOT EXISTS backfill_execucao (
    execucao_curva_id UUID NOT NULL,
    data_referencia_final DATE NOT NULL,
    concorrencia_maxima INT NOT NULL,
    interrupcao_solicitada BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT pk_backfill_execucao PRIMARY KEY (execucao_curva_id),
    CONSTRAINT fk_backfill_execucao_execucao_curva FOREIGN KEY (execucao_curva_id) REFERENCES execucao_curva (id),
    CONSTRAINT ck_backfill_execucao_concorrencia_positiva CHECK (concorrencia_maxima > 0)
);

-- 7. lote_ingestao (V2, V3, V8, V10, V11)
CREATE TABLE IF NOT EXISTS lote_ingestao (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY NOT NULL,
    execucao_curva_id UUID NULL,
    fonte VARCHAR(20) NOT NULL,
    conjunto_dados VARCHAR(50) NOT NULL,
    tipo_payload VARCHAR(20) NOT NULL,
    data_referencia DATE NOT NULL,
    lote_externo_id VARCHAR(100) NOT NULL,
    id_evento VARCHAR(100) NOT NULL,
    hash_payload VARCHAR(80) NOT NULL,
    total_blocos INT NOT NULL,
    blocos_recebidos INT NOT NULL DEFAULT 0,
    pontos_recebidos INT NOT NULL DEFAULT 0,
    pontos_gravados INT NOT NULL DEFAULT 0,
    divergencias CLOB NULL,
    estado VARCHAR(20) NOT NULL,
    recebido_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    pontos_divergentes INT NOT NULL DEFAULT 0,
    correlation_id UUID NULL,
    CONSTRAINT pk_lote_ingestao PRIMARY KEY (id),
    CONSTRAINT uq_lote_ingestao_lote_externo_id UNIQUE (lote_externo_id),
    CONSTRAINT fk_lote_ingestao_execucao_curva FOREIGN KEY (execucao_curva_id) REFERENCES execucao_curva (id),
    CONSTRAINT ck_lote_ingestao_tipo_payload CHECK (tipo_payload IN ('INDIVIDUAL_QUOTES', 'READY_CURVE')),
    CONSTRAINT ck_lote_ingestao_estado CHECK (estado IN ('ABERTO', 'COMPLETO', 'INCOMPLETO'))
);

-- 8. ponto_dado_mercado (V2)
CREATE TABLE IF NOT EXISTS ponto_dado_mercado (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY NOT NULL,
    fonte VARCHAR(20) NOT NULL,
    conjunto_dados VARCHAR(50) NOT NULL,
    data_referencia DATE NOT NULL,
    chave_instrumento VARCHAR(100) NOT NULL,
    valor DECIMAL(28,12) NOT NULL,
    tipo_cotacao VARCHAR(30) NULL,
    data_vencimento DATE NULL,
    lote_ingestao_id BIGINT NOT NULL,
    atualizado_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_ponto_dado_mercado PRIMARY KEY (id),
    CONSTRAINT fk_ponto_dado_mercado_lote_ingestao FOREIGN KEY (lote_ingestao_id) REFERENCES lote_ingestao (id),
    CONSTRAINT uq_ponto_dado_mercado_fonte_conjunto_data_chave UNIQUE (fonte, conjunto_dados, data_referencia, chave_instrumento)
);

-- 9. versao_curva (V4, V19)
CREATE TABLE IF NOT EXISTS versao_curva (
    id UUID NOT NULL DEFAULT RANDOM_UUID(),
    definicao_curva_id UUID NOT NULL,
    versao_definicao_curva_id UUID NOT NULL,
    data_referencia DATE NOT NULL,
    momento_curva VARCHAR(20) NOT NULL,
    numero_versao INT NOT NULL,
    origem_versao VARCHAR(20) NOT NULL,
    estado VARCHAR(20) NOT NULL,
    execucao_curva_id UUID NOT NULL,
    publicado_em TIMESTAMP NULL,
    CONSTRAINT pk_versao_curva PRIMARY KEY (id),
    CONSTRAINT fk_versao_curva_definicao_curva FOREIGN KEY (definicao_curva_id) REFERENCES definicao_curva (id),
    CONSTRAINT fk_versao_curva_versao_definicao_curva FOREIGN KEY (versao_definicao_curva_id) REFERENCES versao_definicao_curva (id),
    CONSTRAINT fk_versao_curva_execucao_curva FOREIGN KEY (execucao_curva_id) REFERENCES execucao_curva (id),
    CONSTRAINT uq_versao_curva_definicao_data_momento_versao UNIQUE (definicao_curva_id, data_referencia, momento_curva, numero_versao),
    CONSTRAINT ck_versao_curva_momento_curva CHECK (momento_curva IN ('ABERTURA', 'INTRADIA', 'FECHAMENTO')),
    CONSTRAINT ck_versao_curva_origem_versao CHECK (origem_versao IN ('CALCULADA', 'IMPORTADA', 'CARREGADA')),
    CONSTRAINT ck_versao_curva_estado CHECK (estado IN ('EM_VALIDACAO', 'PUBLICADA', 'REPROVADA', 'SUBSTITUIDA'))
);

-- 10. vertice_curva (V4)
CREATE TABLE IF NOT EXISTS vertice_curva (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY NOT NULL,
    versao_curva_id UUID NOT NULL,
    prazo_dias_uteis INT NOT NULL,
    prazo_dias_corridos INT NULL,
    data_vencimento DATE NULL,
    taxa DECIMAL(28,12) NOT NULL,
    fator_desconto DECIMAL(28,12) NULL,
    CONSTRAINT pk_vertice_curva PRIMARY KEY (id),
    CONSTRAINT fk_vertice_curva_versao_curva FOREIGN KEY (versao_curva_id) REFERENCES versao_curva (id),
    CONSTRAINT uq_vertice_curva_versao_prazo_uteis UNIQUE (versao_curva_id, prazo_dias_uteis)
);

-- 11. procedencia_curva (V4, V5)
CREATE TABLE IF NOT EXISTS procedencia_curva (
    id UUID NOT NULL DEFAULT RANDOM_UUID(),
    versao_curva_id UUID NOT NULL,
    execucao_curva_id UUID NOT NULL,
    numero_versao_definicao INT NOT NULL,
    checksum_modelo VARCHAR(80) NULL,
    referencias_insumo CLOB NULL,
    hash_conjunto_insumos VARCHAR(80) NULL,
    lote_ingestao_id BIGINT NULL,
    arquivo_carga VARCHAR(400) NULL,
    hash_arquivo VARCHAR(80) NULL,
    carregado_por VARCHAR(100) NULL,
    justificativa CLOB NULL,
    versao_motor VARCHAR(50) NULL,
    criado_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    modelo_curva_id UUID NULL,
    CONSTRAINT pk_procedencia_curva PRIMARY KEY (id),
    CONSTRAINT fk_procedencia_curva_versao_curva FOREIGN KEY (versao_curva_id) REFERENCES versao_curva (id),
    CONSTRAINT fk_procedencia_curva_execucao_curva FOREIGN KEY (execucao_curva_id) REFERENCES execucao_curva (id),
    CONSTRAINT fk_procedencia_curva_lote_ingestao FOREIGN KEY (lote_ingestao_id) REFERENCES lote_ingestao (id),
    CONSTRAINT fk_procedencia_curva_modelo_curva FOREIGN KEY (modelo_curva_id) REFERENCES modelo_curva (id),
    CONSTRAINT uq_procedencia_curva_versao_curva UNIQUE (versao_curva_id)
);

-- 12. validacao_curva (V4, V9)
CREATE TABLE IF NOT EXISTS validacao_curva (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY NOT NULL,
    versao_curva_id UUID NOT NULL,
    teste VARCHAR(60) NOT NULL,
    classificacao VARCHAR(20) NOT NULL,
    resultado VARCHAR(20) NOT NULL,
    medida_observada DECIMAL(28,12) NULL,
    limite_aplicado DECIMAL(28,12) NULL,
    detalhe CLOB NULL,
    executado_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_validacao_curva PRIMARY KEY (id),
    CONSTRAINT fk_validacao_curva_versao_curva FOREIGN KEY (versao_curva_id) REFERENCES versao_curva (id),
    CONSTRAINT uq_validacao_curva_versao_teste UNIQUE (versao_curva_id, teste),
    CONSTRAINT ck_validacao_curva_classificacao CHECK (classificacao IN ('BLOQUEANTE', 'AVISO')),
    CONSTRAINT ck_validacao_curva_resultado CHECK (resultado IN ('APROVADO', 'REPROVADO', 'NAO_APLICAVEL'))
);

-- 13. pendencia_dlq (V6)
CREATE TABLE IF NOT EXISTS pendencia_dlq (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY NOT NULL,
    id_evento VARCHAR(100) NOT NULL,
    correlacao_id UUID NULL,
    motivo VARCHAR(60) NOT NULL,
    detalhe CLOB NULL,
    fonte VARCHAR(20) NULL,
    conjunto_dados VARCHAR(50) NULL,
    data_referencia DATE NULL,
    topico_origem VARCHAR(120) NOT NULL,
    particao_origem INT NULL,
    offset_origem BIGINT NULL,
    topico_dlq VARCHAR(160) NOT NULL,
    particao_dlq INT NULL,
    offset_dlq BIGINT NULL,
    grupo_consumo VARCHAR(80) NULL,
    versao_aplicacao VARCHAR(50) NULL,
    falhou_em TIMESTAMP NOT NULL,
    tentativas INT NOT NULL DEFAULT 1,
    estado VARCHAR(25) NOT NULL,
    desfecho_em TIMESTAMP NULL,
    responsavel VARCHAR(100) NULL,
    justificativa CLOB NULL,
    CONSTRAINT pk_pendencia_dlq PRIMARY KEY (id),
    CONSTRAINT uq_pendencia_dlq_id_evento UNIQUE (id_evento),
    CONSTRAINT ck_pendencia_dlq_estado CHECK (estado IN ('ABERTA', 'EM_REPROCESSAMENTO', 'RESOLVIDA', 'DESCARTADA', 'OBSOLETA'))
);

-- Indices para otimizacao de busca (V7, V14)
CREATE INDEX IF NOT EXISTS ix_versao_curva_vigente ON versao_curva (definicao_curva_id, data_referencia, momento_curva, estado);
CREATE INDEX IF NOT EXISTS ix_ponto_dado_mercado_dia ON ponto_dado_mercado (fonte, conjunto_dados, data_referencia);
CREATE INDEX IF NOT EXISTS ix_execucao_curva_correlacao ON execucao_curva (correlacao_id);
CREATE INDEX IF NOT EXISTS ix_execucao_curva_dia ON execucao_curva (data_referencia, estado);
CREATE INDEX IF NOT EXISTS ix_lote_ingestao_chave ON lote_ingestao (fonte, conjunto_dados, data_referencia);
CREATE INDEX IF NOT EXISTS ix_pendencia_dlq_grupo ON pendencia_dlq (estado, motivo, fonte, conjunto_dados, data_referencia);
CREATE INDEX IF NOT EXISTS ix_validacao_curva_versao ON validacao_curva (versao_curva_id);
