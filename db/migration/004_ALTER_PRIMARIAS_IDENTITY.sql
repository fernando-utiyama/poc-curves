-- ============================================================================
-- Tabelas primárias (*CurvaPrimr): cIdtfdUnic passa de int para bigint IDENTITY(1,1)
-- SQL Server, sobre o schema do 001_SCRIPT_INICIAL.sql. Tabelas sem dados.
--
-- O id deixa de ser gerado pelo código (SELECT MAX + 1 com HOLDLOCK) e o bigint
-- aguenta o delete-and-reinsert, que gasta ids do IDENTITY.
-- Nenhuma tabela tem chave estrangeira para cIdtfdUnic; só a PK usa a coluna.
-- Independe do 002 e do 003 (as colunas deles não são tocadas).
-- ============================================================================

SET XACT_ABORT ON;
BEGIN TRANSACTION;

IF EXISTS (SELECT 1 FROM dbo.tAnbmaCurvaPrimr) OR EXISTS (SELECT 1 FROM dbo.tBbergCurvaPrimr)
   OR EXISTS (SELECT 1 FROM dbo.tBtrsCurvaPrimr) OR EXISTS (SELECT 1 FROM dbo.tCmeCurvaPrimr)
   OR EXISTS (SELECT 1 FROM dbo.tLchCurvaPrimr) OR EXISTS (SELECT 1 FROM dbo.tLsegCurvaPrimr)
   OR EXISTS (SELECT 1 FROM dbo.tTesouCurvaPrimr)
    THROW 50001, 'Há linhas em tabela primária: os ids seriam renumerados. Reavaliar antes de aplicar.', 1;

-- ANBIMA
ALTER TABLE dbo.tAnbmaCurvaPrimr DROP CONSTRAINT XPKtAnbmaCurvaPrimr;
ALTER TABLE dbo.tAnbmaCurvaPrimr DROP COLUMN cIdtfdUnic;
ALTER TABLE dbo.tAnbmaCurvaPrimr ADD cIdtfdUnic bigint IDENTITY(1,1) NOT NULL;
ALTER TABLE dbo.tAnbmaCurvaPrimr ADD CONSTRAINT XPKtAnbmaCurvaPrimr PRIMARY KEY NONCLUSTERED (cIdtfdUnic ASC);

-- Bloomberg
ALTER TABLE dbo.tBbergCurvaPrimr DROP CONSTRAINT XPKtBbergCurvaPrimr;
ALTER TABLE dbo.tBbergCurvaPrimr DROP COLUMN cIdtfdUnic;
ALTER TABLE dbo.tBbergCurvaPrimr ADD cIdtfdUnic bigint IDENTITY(1,1) NOT NULL;
ALTER TABLE dbo.tBbergCurvaPrimr ADD CONSTRAINT XPKtBbergCurvaPrimr PRIMARY KEY NONCLUSTERED (cIdtfdUnic ASC);

-- B3
ALTER TABLE dbo.tBtrsCurvaPrimr DROP CONSTRAINT XPKtBtrsCurvaPrimr;
ALTER TABLE dbo.tBtrsCurvaPrimr DROP COLUMN cIdtfdUnic;
ALTER TABLE dbo.tBtrsCurvaPrimr ADD cIdtfdUnic bigint IDENTITY(1,1) NOT NULL;
ALTER TABLE dbo.tBtrsCurvaPrimr ADD CONSTRAINT XPKtBtrsCurvaPrimr PRIMARY KEY NONCLUSTERED (cIdtfdUnic ASC);

-- CME (a PK tem também o ticker)
ALTER TABLE dbo.tCmeCurvaPrimr DROP CONSTRAINT XPKtCmeCurvaPrimr;
ALTER TABLE dbo.tCmeCurvaPrimr DROP COLUMN cIdtfdUnic;
ALTER TABLE dbo.tCmeCurvaPrimr ADD cIdtfdUnic bigint IDENTITY(1,1) NOT NULL;
ALTER TABLE dbo.tCmeCurvaPrimr ADD CONSTRAINT XPKtCmeCurvaPrimr PRIMARY KEY NONCLUSTERED (cIdtfdUnic ASC, cTickerIndcd ASC);

-- LCH
ALTER TABLE dbo.tLchCurvaPrimr DROP CONSTRAINT XPKtLchCurvaPrimr;
ALTER TABLE dbo.tLchCurvaPrimr DROP COLUMN cIdtfdUnic;
ALTER TABLE dbo.tLchCurvaPrimr ADD cIdtfdUnic bigint IDENTITY(1,1) NOT NULL;
ALTER TABLE dbo.tLchCurvaPrimr ADD CONSTRAINT XPKtLchCurvaPrimr PRIMARY KEY NONCLUSTERED (cIdtfdUnic ASC);

-- LSEG
ALTER TABLE dbo.tLsegCurvaPrimr DROP CONSTRAINT XPKtLsegCurvaPrimr;
ALTER TABLE dbo.tLsegCurvaPrimr DROP COLUMN cIdtfdUnic;
ALTER TABLE dbo.tLsegCurvaPrimr ADD cIdtfdUnic bigint IDENTITY(1,1) NOT NULL;
ALTER TABLE dbo.tLsegCurvaPrimr ADD CONSTRAINT XPKtLsegCurvaPrimr PRIMARY KEY NONCLUSTERED (cIdtfdUnic ASC);

-- Tesouro
ALTER TABLE dbo.tTesouCurvaPrimr DROP CONSTRAINT XPKtTesouCurvaPrimr;
ALTER TABLE dbo.tTesouCurvaPrimr DROP COLUMN cIdtfdUnic;
ALTER TABLE dbo.tTesouCurvaPrimr ADD cIdtfdUnic bigint IDENTITY(1,1) NOT NULL;
ALTER TABLE dbo.tTesouCurvaPrimr ADD CONSTRAINT XPKtTesouCurvaPrimr PRIMARY KEY NONCLUSTERED (cIdtfdUnic ASC);

COMMIT TRANSACTION;
GO
