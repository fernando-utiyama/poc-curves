-- ============================================================================
-- 001_SCRIPT_INICIAL.sql
-- Replicação do script 001_SCRIPT_INICIAL.sql (schema legado real de curvas de
-- mercado, fonte: sistema com BloombergCurvaPrimr.java) — TRANSCRITO A PARTIR
-- DE FOTOS DE TELA, não copiado byte a byte do arquivo original (o arquivo não
-- estava disponível nesta máquina). Conferir contra o script real antes de
-- tratar como definitivo — ver ressalvas abaixo.
--
-- Ressalvas da transcrição, já conferidas contra o arquivo original pelo usuário:
-- 1. FK_tCurvaMercd_tTesouCurvaPrimr (não visível em nenhuma foto, inferida
--    pelo padrão das demais tabelas "*CurvaPrimr") — CONFIRMADA, existe no
--    script real com esse nome exato.
-- 2. Prefixo de coluna "cIdtfdUnic"/"cIdtfdConfg"/"cIdtfdTrefa"/"cIdtfdParm"/
--    "cIdtfdEntrd" — CONFIRMADO como "I" maiúsculo (de "Identificador", como
--    em cTickerIdtfdUnic), conferido no arquivo real pelo Copilot em
--    2026-09-30; a grafia com "l" minúsculo era erro de leitura das fotos.
-- 3. DECIMAL(28,16) em vFatorAcum/vFatorDia/vDiaFator/vFatorCalc/vAcumFator
--    (vs. DECIMAL(28,12) nos demais campos vPreco*/vFator*) — CONFIRMADO.
-- 4. Ajustes da change banco-curvas-ajustes já incorporados ao script real:
--    tBbergCurvaPrimr.cTickerBberg VARCHAR(50) (era CHAR(20)) e tCurvaData
--    removida (só sobram os DROP ... IF EXISTS no início, para bancos antigos).
--
-- Serviços que usam este schema: services/processor, services/engine,
-- services/curves e services/orchestrator. Mudanças de schema vão como
-- script para o dono do banco (change banco-curvas-ajustes).
-- ============================================================================

IF EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[tTesouCurvaPrimr]') AND type in (N'U'))
ALTER TABLE [dbo].[tTesouCurvaPrimr] DROP CONSTRAINT IF EXISTS [FK_tCurvaMercd_tTesouCurvaPrimr]
GO

IF EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[tParmTrefa]') AND type in (N'U'))
ALTER TABLE [dbo].[tParmTrefa] DROP CONSTRAINT IF EXISTS [FK_tTrefaAgnda_tParmTrefa]
GO

IF EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[tParmTpoGrade]') AND type in (N'U'))
ALTER TABLE [dbo].[tParmTpoGrade] DROP CONSTRAINT IF EXISTS [FK_tGradeTpo_tParmTpoGrade]
GO

IF EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[tParmConfgCurva]') AND type in (N'U'))
ALTER TABLE [dbo].[tParmConfgCurva] DROP CONSTRAINT IF EXISTS [FK_tConfgCurva_tParmConfgCurva]
GO

IF EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[tMtrizCurva]') AND type in (N'U'))
ALTER TABLE [dbo].[tMtrizCurva] DROP CONSTRAINT IF EXISTS [FK_tCurvaMercd_tMtrizCurva]
GO

IF EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[tLsegCurvaPrimr]') AND type in (N'U'))
ALTER TABLE [dbo].[tLsegCurvaPrimr] DROP CONSTRAINT IF EXISTS [FK_tCurvaMercd_tLsegCurvaPrimr]
GO

IF EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[tLogTrefa]') AND type in (N'U'))
ALTER TABLE [dbo].[tLogTrefa] DROP CONSTRAINT IF EXISTS [FK_tTrefaAgnda_tLogTrefa]
GO

IF EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[tLchCurvaPrimr]') AND type in (N'U'))
ALTER TABLE [dbo].[tLchCurvaPrimr] DROP CONSTRAINT IF EXISTS [FK_tCurvaMercd_tLchCurvaPrimr]
GO

IF EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[tGradeVoltl]') AND type in (N'U'))
ALTER TABLE [dbo].[tGradeVoltl] DROP CONSTRAINT IF EXISTS [FK_tGradeTpo_tGradeVoltl]
GO

IF EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[tGradeMtrizData]') AND type in (N'U'))
ALTER TABLE [dbo].[tGradeMtrizData] DROP CONSTRAINT IF EXISTS [FK_tGradeVoltl_tGradeMtrizData]
GO

IF EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[tDadoVertcCurva]') AND type in (N'U'))
ALTER TABLE [dbo].[tDadoVertcCurva] DROP CONSTRAINT IF EXISTS [FK_tCurvaMercd_tDadoVertcCurva]
GO

IF EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[tDadoCurva]') AND type in (N'U'))
ALTER TABLE [dbo].[tDadoCurva] DROP CONSTRAINT IF EXISTS [FK_tCurvaMercd_tDadoCurva]
GO

IF EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[tCurvaPrvdr]') AND type in (N'U'))
ALTER TABLE [dbo].[tCurvaPrvdr] DROP CONSTRAINT IF EXISTS [FK_tPrvdrDadoMercd_tCurvaPrvdr]
GO

IF EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[tCurvaPrvdr]') AND type in (N'U'))
ALTER TABLE [dbo].[tCurvaPrvdr] DROP CONSTRAINT IF EXISTS [FK_tCurvaMercd_tCurvaPrvdr]
GO

IF EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[tCurvaData]') AND type in (N'U'))
ALTER TABLE [dbo].[tCurvaData] DROP CONSTRAINT IF EXISTS [FK_tDadoCurva_tCurvaData]
GO

IF EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[tConfgCurva]') AND type in (N'U'))
ALTER TABLE [dbo].[tConfgCurva] DROP CONSTRAINT IF EXISTS [FK_tCurvaMercd_tConfgCurva]
GO

IF EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[tCmeCurvaPrimr]') AND type in (N'U'))
ALTER TABLE [dbo].[tCmeCurvaPrimr] DROP CONSTRAINT IF EXISTS [FK_tCurvaMercd_tCmeCurvaPrimr]
GO

IF EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[tBtrsCurvaPrimr]') AND type in (N'U'))
ALTER TABLE [dbo].[tBtrsCurvaPrimr] DROP CONSTRAINT IF EXISTS [FK_tCurvaMercd_tBtrsCurvaPrimr]
GO

IF EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[tBbergCurvaPrimr]') AND type in (N'U'))
ALTER TABLE [dbo].[tBbergCurvaPrimr] DROP CONSTRAINT IF EXISTS [FK_tCurvaMercd_tBbergCurvaPrimr]
GO

IF EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[tAnbmaCurvaPrimr]') AND type in (N'U'))
ALTER TABLE [dbo].[tAnbmaCurvaPrimr] DROP CONSTRAINT IF EXISTS [FK_tCurvaMercd_tAnbmaCurvaPrimr]
GO

DROP TABLE IF EXISTS [dbo].[tTrefaAgnda]
GO
DROP TABLE IF EXISTS [dbo].[tTesouCurvaPrimr]
GO
DROP TABLE IF EXISTS [dbo].[tPrvdrDadoMercd]
GO
DROP TABLE IF EXISTS [dbo].[tParmTrefa]
GO
DROP TABLE IF EXISTS [dbo].[tParmTpoGrade]
GO
DROP TABLE IF EXISTS [dbo].[tParmConfgCurva]
GO
DROP TABLE IF EXISTS [dbo].[tMtrizCurva]
GO
DROP TABLE IF EXISTS [dbo].[tLsegCurvaPrimr]
GO
DROP TABLE IF EXISTS [dbo].[tLogTrefa]
GO
DROP TABLE IF EXISTS [dbo].[tLchCurvaPrimr]
GO
DROP TABLE IF EXISTS [dbo].[tGradeVoltl]
GO
DROP TABLE IF EXISTS [dbo].[tGradeTpo]
GO
DROP TABLE IF EXISTS [dbo].[tGradeMtrizData]
GO
DROP TABLE IF EXISTS [dbo].[tDadoVertcCurva]
GO
DROP TABLE IF EXISTS [dbo].[tDadoCurva]
GO
DROP TABLE IF EXISTS [dbo].[tCurvaPrvdr]
GO
DROP TABLE IF EXISTS [dbo].[tCurvaMercd]
GO
DROP TABLE IF EXISTS [dbo].[tCurvaData]
GO
DROP TABLE IF EXISTS [dbo].[tConfgCurva]
GO
DROP TABLE IF EXISTS [dbo].[tCmeCurvaPrimr]
GO
DROP TABLE IF EXISTS [dbo].[tBtrsCurvaPrimr]
GO
DROP TABLE IF EXISTS [dbo].[tBbergCurvaPrimr]
GO
DROP TABLE IF EXISTS [dbo].[tAnbmaCurvaPrimr]
GO

-- ============================================================================
-- CREATE TABLE
-- ============================================================================

CREATE TABLE [tAnbmaCurvaPrimr]
(
    [cIdtfdUnic]        int             NOT NULL ,
    [cTickerIndcd]      varchar(50)     NOT NULL ,
    [dBaseReft]         date            NULL ,
    [vPrecoTx]          DECIMAL(28,12)  NULL ,
    [vVertcCurva]       DECIMAL(28,12)  NULL
)
GO

CREATE TABLE [tBbergCurvaPrimr]
(
    [cIdtfdUnic]        int             NOT NULL ,
    [cTickerIndcd]      varchar(50)     NOT NULL ,
    [cDiaVcto]          int             NULL ,
    [cFormaLiqdc]       char(20)        NULL ,
    [cTickerBberg]      varchar(50)     NULL ,
    [dBaseReft]         date            NULL ,
    [dLiqdcFincr]       date            NULL ,
    [dVctoContr]        date            NULL ,
    [dUltNegoc]         date            NULL ,
    [vPrecoLiqdc]       DECIMAL(28,12)  NULL ,
    [vPrecoMed]         DECIMAL(28,12)  NULL ,
    [vPrecoUlt]         DECIMAL(28,12)  NULL
)
GO

CREATE TABLE [tBtrsCurvaPrimr]
(
    [cIdtfdUnic]        int             NOT NULL ,
    [cTickerIndcd]      varchar(50)     NOT NULL ,
    [cDiaCorri]         int             NULL ,
    [cDiaUtil]          int             NULL ,
    [dBaseReft]         date            NULL ,
    [vFatorAcum]        DECIMAL(28,16)  NULL ,
    [vPrecoTx]          DECIMAL(28,12)  NULL ,
    [vFatorDia]         DECIMAL(28,16)  NULL
)
GO

CREATE TABLE [tCmeCurvaPrimr]
(
    [cIdtfdUnic]        int             NOT NULL ,
    [cTickerIndcd]      varchar(50)     NOT NULL ,
    [cDsvioAjustCurva]  int             NULL ,
    [dBaseReft]         date            NULL ,
    [vFatorDesc]        DECIMAL(28,12)  NULL
)
GO

CREATE TABLE [tConfgCurva]
(
    [cIdtfdConfg]       int             IDENTITY(1,1) NOT NULL ,
    [cTickerIndcd]      varchar(50)     NULL ,
    [cAtivoFincr]       bit             NULL ,
    [cRotnaCalc]        varchar(1024)   NULL ,
    [cPosCalc]          varchar(1024)   NULL ,
    [cPreCalc]          varchar(1024)   NULL ,
    [cLingSist]         varchar(1024)   NULL ,
    [cModDado]          varchar(1024)   NULL ,
    [cMotorCalc]        varchar(1024)   NULL ,
    [cPosMotorCalc]     varchar(1024)   NULL ,
    [cPreMotorCalc]     varchar(1024)   NULL ,
    [cTpoInstt]         varchar(50)     NULL ,
    [cVrsaoReg]         int             NULL ,
    [dInicVgcia]        date            NULL ,
    [dValidAte]         date            NULL
)
GO

CREATE TABLE [tCurvaMercd]
(
    [cTickerIndcd]      varchar(50)     NOT NULL ,
    [cClasfInstt]       varchar(50)     NULL ,
    [cClassAtivo]       varchar(50)     NULL ,
    [cPprioDado]        varchar(50)     NULL ,
    [cConfgIdtfd]       int             NULL ,
    [cCurvaReft]        varchar(50)     NULL ,
    [cFamlInsttFincr]   varchar(50)     NULL ,
    [cIndxdAtivo]       varchar(50)     NULL ,
    [cMoedaNegoc]       varchar(1024)   NULL ,
    [cNormaDia]         char(20)        NULL ,
    [cPaisInstt]        char(20)        NULL ,
    [cSitReg]           char(20)        NULL ,
    [cTickerIdtfdUnic]  varchar(50)     NULL ,
    [cTpoCotac]         char(30)        NULL ,
    [cTpoJuro]          char(20)        NULL ,
    [cTpoVlr]           char(30)        NULL ,
    [cUsuarAtulz]       varchar(100)    NULL ,
    [cUsuarCalc]        varchar(100)    NULL ,
    [dCriacReg]         datetime        NULL ,
    [dBaseReft]         date            NULL ,
    [dInicVgcia]        date            NULL ,
    [dUltAtulz]         datetime        NULL ,
    [dValidAte]         date            NULL ,
    [iPrvdrDados]       varchar(1024)   NULL ,
    [rAtivoIndcd]       varchar(1024)   NULL ,
    [vFatorMultiAtivo]  DECIMAL(28,12)  NULL
)
GO

CREATE TABLE [tCurvaPrvdr]
(
    [cIdtfdUnic]        int             NOT NULL ,
    [cPriorCsumo]       int             NULL ,
    [cPrvdrMercd]       varchar(50)     NULL ,
    [cTickerIndcd]      varchar(50)     NOT NULL ,
    [cTickerPrvdr]      varchar(1024)   NULL ,
    [iPrvdrDados]       varchar(1024)   NULL
)
GO

CREATE TABLE [tDadoCurva]
(
    [dBaseReft]         date            NOT NULL ,
    [cTickerIndcd]      varchar(50)     NOT NULL ,
    [dVertcReft]        date            NOT NULL ,
    [vPrecoTx]          DECIMAL(28,12)  NULL
)
GO

CREATE TABLE [tDadoVertcCurva]
(
    [dBaseReft]         date            NOT NULL ,
    [cTickerIndcd]      varchar(50)     NOT NULL ,
    [dVertcReft]        date            NOT NULL ,
    [cDiaUtil]          int             NULL ,
    [vFatorDia]         DECIMAL(28,16)  NULL ,
    [vFatorAcum]        DECIMAL(28,16)  NULL ,
    [cQtdDiaPer]        int             NULL ,
    [cQtdDiaReft]       int             NULL ,
    [vPrecoTx]          DECIMAL(28,12)  NULL
)
GO

-- Descrições de tDadoVertcCurva: NÃO existem no script original aplicado.
-- Foram trazidas da ideia original do modelo (onde a data do vértice se chamava
-- dtVerticeReferencia) só para documentar o significado das colunas no poc.
EXEC sp_addextendedproperty @name = N'MS_Description', @value = N'Informações detalhadas de cada vértice da curva.',
    @level0type = N'SCHEMA', @level0name = N'dbo', @level1type = N'TABLE', @level1name = N'tDadoVertcCurva'
GO
EXEC sp_addextendedproperty @name = N'MS_Description', @value = N'Ticker que representa o ativo ou indicador.',
    @level0type = N'SCHEMA', @level0name = N'dbo', @level1type = N'TABLE', @level1name = N'tDadoVertcCurva', @level2type = N'COLUMN', @level2name = N'cTickerIndcd'
GO
EXEC sp_addextendedproperty @name = N'MS_Description', @value = N'Quantidade de dias uteis considerados no calculo.',
    @level0type = N'SCHEMA', @level0name = N'dbo', @level1type = N'TABLE', @level1name = N'tDadoVertcCurva', @level2type = N'COLUMN', @level2name = N'cDiaUtil'
GO
EXEC sp_addextendedproperty @name = N'MS_Description', @value = N'Fator diario de capitalizacao.',
    @level0type = N'SCHEMA', @level0name = N'dbo', @level1type = N'TABLE', @level1name = N'tDadoVertcCurva', @level2type = N'COLUMN', @level2name = N'vFatorDia'
GO
EXEC sp_addextendedproperty @name = N'MS_Description', @value = N'Fator acumulado ate a data de referencia.',
    @level0type = N'SCHEMA', @level0name = N'dbo', @level1type = N'TABLE', @level1name = N'tDadoVertcCurva', @level2type = N'COLUMN', @level2name = N'vFatorAcum'
GO
EXEC sp_addextendedproperty @name = N'MS_Description', @value = N'Valor da taxa ou preco associado ao ativo na data de referencia.',
    @level0type = N'SCHEMA', @level0name = N'dbo', @level1type = N'TABLE', @level1name = N'tDadoVertcCurva', @level2type = N'COLUMN', @level2name = N'vPrecoTx'
GO
EXEC sp_addextendedproperty @name = N'MS_Description', @value = N'Quantidade de dias do período.',
    @level0type = N'SCHEMA', @level0name = N'dbo', @level1type = N'TABLE', @level1name = N'tDadoVertcCurva', @level2type = N'COLUMN', @level2name = N'cQtdDiaPer'
GO
EXEC sp_addextendedproperty @name = N'MS_Description', @value = N'Quantidade de dias em convenção 30/360.',
    @level0type = N'SCHEMA', @level0name = N'dbo', @level1type = N'TABLE', @level1name = N'tDadoVertcCurva', @level2type = N'COLUMN', @level2name = N'cQtdDiaReft'
GO
EXEC sp_addextendedproperty @name = N'MS_Description', @value = N'Data base das informações.',
    @level0type = N'SCHEMA', @level0name = N'dbo', @level1type = N'TABLE', @level1name = N'tDadoVertcCurva', @level2type = N'COLUMN', @level2name = N'dBaseReft'
GO
EXEC sp_addextendedproperty @name = N'MS_Description', @value = N'Data de Referencia do Vértice',
    @level0type = N'SCHEMA', @level0name = N'dbo', @level1type = N'TABLE', @level1name = N'tDadoVertcCurva', @level2type = N'COLUMN', @level2name = N'dVertcReft'
GO

CREATE TABLE [tGradeMtrizData]
(
    [cIdtfdUnic]        int             NOT NULL ,
    [dBaseReft]         date            NULL ,
    [cFormtArq]         varchar(200)    NULL ,
    [cModLyoutCarga]    char(16)        NULL ,
    [cTickerPrvdr]      varchar(1024)   NOT NULL ,
    [cTpoCotac]         char(30)        NULL ,
    [cVrsaoReg]         int             NULL ,
    [dCriacReg]         datetime        NULL ,
    [dUltAtulz]         datetime        NULL
)
GO

CREATE TABLE [tGradeTpo]
(
    [cTpoInstt]         varchar(50)     NOT NULL ,
    [cCalcMotor]        varchar(1024)   NULL ,
    [dVrsaoMod]         date            NULL
)
GO

CREATE TABLE [tGradeVoltl]
(
    [cTickerPrvdr]      varchar(1024)   NOT NULL ,
    [cExpirCad]         bit             NULL ,
    [cSedolAtivo]       char(30)        NULL ,
    [dCriacReg]         datetime        NULL ,
    [dUltAtulz]         datetime        NULL ,
    [cTpoInstt]         varchar(50)     NOT NULL ,
    [cTickerBberg]      varchar(50)     NULL ,
    [iRegAtivo]         varchar(1024)   NULL
)
GO

CREATE TABLE [tLchCurvaPrimr]
(
    [cIdtfdUnic]        int             NOT NULL ,
    [cTickerIndcd]      varchar(50)     NOT NULL ,
    [cCurvaCompd]       int             NULL ,
    [cTpoInsttCurva]    varchar(50)     NULL ,
    [dBaseReft]         date            NULL ,
    [dVctoContr]        date            NULL ,
    [vAcumFator]        DECIMAL(28,16)  NULL ,
    [vFatorDesc]        DECIMAL(28,12)  NULL ,
    [vTxEquil]          DECIMAL(28,12)  NULL ,
    [vTxZero]           DECIMAL(28,12)  NULL
)
GO

CREATE TABLE [tLogTrefa]
(
    [cIdtfdEntrd]       int             IDENTITY(1,1) NOT NULL ,
    [cIdtfdTrefa]       int             NOT NULL ,
    [cSitExcuc]         int             NULL ,
    [dAtaCriac]         datetime        NOT NULL ,
    [rLogTrefa]         varchar(8000)   NULL
)
GO

CREATE TABLE [tLsegCurvaPrimr]
(
    [cIdtfdUnic]        int             NOT NULL ,
    [cTickerIndcd]      varchar(50)     NOT NULL ,
    [cDiaVcto]          int             NULL ,
    [cFormaLiqdc]       char(20)        NULL ,
    [dBaseReft]         date            NULL ,
    [dVctoContr]        date            NULL ,
    [dUltNegoc]         date            NULL ,
    [vPrecoLiqdc]       DECIMAL(28,12)  NULL ,
    [vPrecoMed]         DECIMAL(28,12)  NULL ,
    [vPrecoUlt]         DECIMAL(28,12)  NULL
)
GO

CREATE TABLE [tMtrizCurva]
(
    [cTickerIndcd]      varchar(50)     NOT NULL ,
    [dBaseReft]         date            NOT NULL ,
    [cFormtArq]         varchar(200)    NULL ,
    [cModLyoutCarga]    char(16)        NULL ,
    [cTpoInstt]         varchar(50)     NULL ,
    [cVrsaoReg]         int             NULL ,
    [dCriacReg]         datetime        NULL ,
    [dUltAtulz]         datetime        NULL
)
GO

CREATE TABLE [tParmConfgCurva]
(
    [cIdtfdConfg]       int             NOT NULL ,
    [cTpoInstt]         varchar(50)     NULL ,
    [iPrvdrDados]       varchar(1024)   NULL ,
    [vPrecoTx]          DECIMAL(28,12)  NULL ,
    [cConfgIdtfd]       int             NULL
)
GO

CREATE TABLE [tParmTpoGrade]
(
    [cIdtfdUnic]        int             NOT NULL ,
    [cTpoParm]          varchar(255)    NULL ,
    [cParmConfg]        varchar(8000)   NULL ,
    [iParmConfg]        varchar(50)     NULL ,
    [cTpoInstt]         varchar(50)     NULL
)
GO

CREATE TABLE [tParmTrefa]
(
    [cIdtfdParm]        int             IDENTITY(1,1) NOT NULL ,
    [cIdtfdTrefa]       int             NOT NULL ,
    [cCategParmConfg]   varchar(255)    NULL ,
    [iParmTrefa]        varchar(255)    NULL ,
    [rParmTrefa]        varchar(8000)   NULL
)
GO

CREATE TABLE [tPrvdrDadoMercd]
(
    [iPrvdrDados]       varchar(1024)   NOT NULL ,
    [cInfoProdt]        varchar(1024)   NULL ,
    [cProdt]            varchar(1024)   NULL ,
    [iCoplt]            varchar(50)     NULL
)
GO

CREATE TABLE [tTesouCurvaPrimr]
(
    [cIdtfdUnic]        int             NOT NULL ,
    [dBaseReft]         date            NULL ,
    [cNegocDay]         int             NULL ,
    [cQtdDiaPer]        int             NULL ,
    [cQtdDiaReft]       int             NULL ,
    [cTickerIndcd]      varchar(50)     NULL ,
    [vDiaFator]         DECIMAL(28,16)  NULL ,
    [vFatorCalc]        DECIMAL(28,16)  NULL ,
    [vPrecoTx]          DECIMAL(28,12)  NULL
)
GO

CREATE TABLE [tTrefaAgnda]
(
    [cIdtfdTrefa]       int             IDENTITY(1,1) NOT NULL ,
    [cAcaoOperSist]     varchar(1024)   NOT NULL ,
    [cRegraAgnda]       char(15)        NULL ,
    [cRegraIntvl]       char(20)        NULL ,
    [cSit]              char(30)        NULL ,
    [iParmTrefa]        varchar(255)    NULL ,
    [rTrefa]            varchar(1024)   NULL
)
GO

-- ============================================================================
-- PRIMARY KEYS / ÍNDICES
-- ============================================================================

ALTER TABLE [tAnbmaCurvaPrimr]
    ADD CONSTRAINT [XPKtAnbmaCurvaPrimr] PRIMARY KEY NONCLUSTERED ([cIdtfdUnic] ASC)
GO

CREATE NONCLUSTERED INDEX [XIF1tAnbmaCurvaPrimr] ON [tAnbmaCurvaPrimr]
(
    [cTickerIndcd] ASC
)
GO

ALTER TABLE [tBbergCurvaPrimr]
    ADD CONSTRAINT [XPKtBbergCurvaPrimr] PRIMARY KEY NONCLUSTERED ([cIdtfdUnic] ASC)
GO

CREATE NONCLUSTERED INDEX [XIF1tBbergCurvaPrimr] ON [tBbergCurvaPrimr]
(
    [cTickerIndcd] ASC
)
GO

ALTER TABLE [tBtrsCurvaPrimr]
    ADD CONSTRAINT [XPKtBtrsCurvaPrimr] PRIMARY KEY NONCLUSTERED ([cIdtfdUnic] ASC)
GO

ALTER TABLE [tCmeCurvaPrimr]
    ADD CONSTRAINT [XPKtCmeCurvaPrimr] PRIMARY KEY NONCLUSTERED ([cIdtfdUnic] ASC, [cTickerIndcd] ASC)
GO

CREATE NONCLUSTERED INDEX [XIF1tCmeCurvaPrimr] ON [tCmeCurvaPrimr]
(
    [cTickerIndcd] ASC
)
GO

ALTER TABLE [tConfgCurva]
    ADD CONSTRAINT [XPKtConfgCurva] PRIMARY KEY NONCLUSTERED ([cIdtfdConfg] ASC)
GO

CREATE NONCLUSTERED INDEX [XIF1tConfgCurva] ON [tConfgCurva]
(
    [cTickerIndcd] ASC
)
GO

ALTER TABLE [tCurvaMercd]
    ADD CONSTRAINT [XPKtCurvaMercd] PRIMARY KEY NONCLUSTERED ([cTickerIndcd] ASC)
GO

ALTER TABLE [tCurvaPrvdr]
    ADD CONSTRAINT [XPKtCurvaPrvdr] PRIMARY KEY NONCLUSTERED ([cIdtfdUnic] ASC)
GO

CREATE NONCLUSTERED INDEX [XIF2tCurvaPrvdr] ON [tCurvaPrvdr]
(
    [iPrvdrDados] ASC
)
GO

ALTER TABLE [tDadoCurva]
    ADD CONSTRAINT [XPKtDadoCurva] PRIMARY KEY NONCLUSTERED ([dBaseReft] ASC, [cTickerIndcd] ASC, [dVertcReft] ASC)
GO

ALTER TABLE [tDadoVertcCurva]
    ADD CONSTRAINT [XPKtDadoVertcCurva] PRIMARY KEY NONCLUSTERED ([dBaseReft] ASC, [cTickerIndcd] ASC, [dVertcReft] ASC)
GO

ALTER TABLE [tGradeMtrizData]
    ADD CONSTRAINT [XPKtGradeMtrizData] PRIMARY KEY NONCLUSTERED ([cIdtfdUnic] ASC)
GO

ALTER TABLE [tGradeTpo]
    ADD CONSTRAINT [XPKtGradeTpo] PRIMARY KEY NONCLUSTERED ([cTpoInstt] ASC)
GO

ALTER TABLE [tGradeVoltl]
    ADD CONSTRAINT [XPKtGradeVoltl] PRIMARY KEY NONCLUSTERED ([cTickerPrvdr] ASC)
GO

CREATE NONCLUSTERED INDEX [XIF1tGradeVoltl] ON [tGradeVoltl]
(
    [cTpoInstt] ASC
)
GO

ALTER TABLE [tLchCurvaPrimr]
    ADD CONSTRAINT [XPKtLchCurvaPrimr] PRIMARY KEY NONCLUSTERED ([cIdtfdUnic] ASC)
GO

CREATE NONCLUSTERED INDEX [XIF1tLchCurvaPrimr] ON [tLchCurvaPrimr]
(
    [cTickerIndcd] ASC
)
GO

ALTER TABLE [tLogTrefa]
    ADD CONSTRAINT [XPKtLogTrefa] PRIMARY KEY NONCLUSTERED ([cIdtfdEntrd] ASC)
GO

CREATE NONCLUSTERED INDEX [XIF1tLogTrefa] ON [tLogTrefa]
(
    [cIdtfdTrefa] ASC
)
GO

ALTER TABLE [tLsegCurvaPrimr]
    ADD CONSTRAINT [XPKtLsegCurvaPrimr] PRIMARY KEY NONCLUSTERED ([cIdtfdUnic] ASC)
GO

CREATE NONCLUSTERED INDEX [XIF1tLsegCurvaPrimr] ON [tLsegCurvaPrimr]
(
    [cTickerIndcd] ASC
)
GO

ALTER TABLE [tMtrizCurva]
    ADD CONSTRAINT [XPKtMtrizCurva] PRIMARY KEY NONCLUSTERED ([cTickerIndcd] ASC, [dBaseReft] ASC)
GO

CREATE NONCLUSTERED INDEX [XIF1tMtrizCurva] ON [tMtrizCurva]
(
    [cTickerIndcd] ASC
)
GO

ALTER TABLE [tParmConfgCurva]
    ADD CONSTRAINT [XPKtParmConfgCurva] PRIMARY KEY NONCLUSTERED ([cIdtfdConfg] ASC)
GO

CREATE UNIQUE NONCLUSTERED INDEX [XIF1tParmConfgCurva] ON [tParmConfgCurva]
(
    [cIdtfdConfg] ASC
)
GO

ALTER TABLE [tParmTpoGrade]
    ADD CONSTRAINT [XPKtParmTpoGrade] PRIMARY KEY CLUSTERED ([cIdtfdUnic] ASC)
GO

CREATE NONCLUSTERED INDEX [XIF1tParmTpoGrade] ON [tParmTpoGrade]
(
    [cTpoInstt] ASC
)
GO

ALTER TABLE [tParmTrefa]
    ADD CONSTRAINT [XPKtParmTrefa] PRIMARY KEY NONCLUSTERED ([cIdtfdParm] ASC)
GO

CREATE NONCLUSTERED INDEX [XIF1tParmTrefa] ON [tParmTrefa]
(
    [cIdtfdTrefa] ASC
)
GO

ALTER TABLE [tPrvdrDadoMercd]
    ADD CONSTRAINT [XPKtPrvdrDadoMercd] PRIMARY KEY NONCLUSTERED ([iPrvdrDados] ASC)
GO

ALTER TABLE [tTesouCurvaPrimr]
    ADD CONSTRAINT [XPKtTesouCurvaPrimr] PRIMARY KEY NONCLUSTERED ([cIdtfdUnic] ASC)
GO

CREATE NONCLUSTERED INDEX [XIF1tTesouCurvaPrimr] ON [tTesouCurvaPrimr]
(
    [cTickerIndcd] ASC
)
GO

ALTER TABLE [tTrefaAgnda]
    ADD CONSTRAINT [XPKtTrefa] PRIMARY KEY NONCLUSTERED ([cIdtfdTrefa] ASC)
GO

-- ============================================================================
-- FOREIGN KEYS
-- ============================================================================

ALTER TABLE [tAnbmaCurvaPrimr]
    ADD CONSTRAINT [FK_tCurvaMercd_tAnbmaCurvaPrimr] FOREIGN KEY([cTickerIndcd]) REFERENCES [tCurvaMercd]([cTickerIndcd])
GO

ALTER TABLE [tBbergCurvaPrimr]
    ADD CONSTRAINT [FK_tCurvaMercd_tBbergCurvaPrimr] FOREIGN KEY([cTickerIndcd]) REFERENCES [tCurvaMercd]([cTickerIndcd])
GO

ALTER TABLE [tBtrsCurvaPrimr]
    ADD CONSTRAINT [FK_tCurvaMercd_tBtrsCurvaPrimr] FOREIGN KEY([cTickerIndcd]) REFERENCES [tCurvaMercd]([cTickerIndcd])
GO

ALTER TABLE [tCmeCurvaPrimr]
    ADD CONSTRAINT [FK_tCurvaMercd_tCmeCurvaPrimr] FOREIGN KEY([cTickerIndcd]) REFERENCES [tCurvaMercd]([cTickerIndcd])
GO

ALTER TABLE [tConfgCurva]
    ADD CONSTRAINT [FK_tCurvaMercd_tConfgCurva] FOREIGN KEY([cTickerIndcd]) REFERENCES [tCurvaMercd]([cTickerIndcd])
GO

ALTER TABLE [tCurvaPrvdr]
    ADD CONSTRAINT [FK_tCurvaMercd_tCurvaPrvdr] FOREIGN KEY([cTickerIndcd]) REFERENCES [tCurvaMercd]([cTickerIndcd])
GO

ALTER TABLE [tCurvaPrvdr]
    ADD CONSTRAINT [FK_tPrvdrDadoMercd_tCurvaPrvdr] FOREIGN KEY([iPrvdrDados]) REFERENCES [tPrvdrDadoMercd]([iPrvdrDados])
GO

ALTER TABLE [tDadoCurva]
    ADD CONSTRAINT [FK_tCurvaMercd_tDadoCurva] FOREIGN KEY([cTickerIndcd]) REFERENCES [tCurvaMercd]([cTickerIndcd])
GO

ALTER TABLE [tDadoVertcCurva]
    ADD CONSTRAINT [FK_tCurvaMercd_tDadoVertcCurva] FOREIGN KEY([cTickerIndcd]) REFERENCES [tCurvaMercd]([cTickerIndcd])
GO

ALTER TABLE [tGradeMtrizData]
    ADD CONSTRAINT [FK_tGradeVoltl_tGradeMtrizData] FOREIGN KEY([cTickerPrvdr]) REFERENCES [tGradeVoltl]([cTickerPrvdr])
GO

ALTER TABLE [tGradeVoltl]
    ADD CONSTRAINT [FK_tGradeTpo_tGradeVoltl] FOREIGN KEY([cTpoInstt]) REFERENCES [tGradeTpo]([cTpoInstt])
GO

ALTER TABLE [tLchCurvaPrimr]
    ADD CONSTRAINT [FK_tCurvaMercd_tLchCurvaPrimr] FOREIGN KEY([cTickerIndcd]) REFERENCES [tCurvaMercd]([cTickerIndcd])
GO

ALTER TABLE [tLogTrefa]
    ADD CONSTRAINT [FK_tTrefaAgnda_tLogTrefa] FOREIGN KEY([cIdtfdTrefa]) REFERENCES [tTrefaAgnda]([cIdtfdTrefa])
GO

ALTER TABLE [tLsegCurvaPrimr]
    ADD CONSTRAINT [FK_tCurvaMercd_tLsegCurvaPrimr] FOREIGN KEY([cTickerIndcd]) REFERENCES [tCurvaMercd]([cTickerIndcd])
GO

ALTER TABLE [tMtrizCurva]
    ADD CONSTRAINT [FK_tCurvaMercd_tMtrizCurva] FOREIGN KEY([cTickerIndcd]) REFERENCES [tCurvaMercd]([cTickerIndcd])
GO

ALTER TABLE [tParmConfgCurva]
    ADD CONSTRAINT [FK_tConfgCurva_tParmConfgCurva] FOREIGN KEY([cIdtfdConfg]) REFERENCES [tConfgCurva]([cIdtfdConfg])
GO

ALTER TABLE [tParmTpoGrade]
    ADD CONSTRAINT [FK_tGradeTpo_tParmTpoGrade] FOREIGN KEY([cTpoInstt]) REFERENCES [tGradeTpo]([cTpoInstt])
GO

ALTER TABLE [tParmTrefa]
    ADD CONSTRAINT [FK_tTrefaAgnda_tParmTrefa] FOREIGN KEY([cIdtfdTrefa]) REFERENCES [tTrefaAgnda]([cIdtfdTrefa])
GO

-- Não capturada em nenhuma foto (a última imagem corta logo após a FK acima) —
-- INFERIDA pelo mesmo padrão usado por toda tabela "*CurvaPrimr" (FK de
-- cTickerIndcd para tCurvaMercd). Confirmar contra o script original.
ALTER TABLE [tTesouCurvaPrimr]
    ADD CONSTRAINT [FK_tCurvaMercd_tTesouCurvaPrimr] FOREIGN KEY([cTickerIndcd]) REFERENCES [tCurvaMercd]([cTickerIndcd])
GO
