-- ============================================================================
-- Ajustes de schema das curvas de mercado (change banco-curvas-ajustes)
-- SQL Server, sobre o schema real (o do 001_SCRIPT_INICIAL.sql). Banco ainda sem uso: tabelas
-- vazias, sem migração de dados.
--
-- 1. tBbergCurvaPrimr.cTickerBberg: CHAR(20) -> VARCHAR(50)
--    (ticker Bloomberg completo, ex.: 'S0490Z 15M BLC2 Curncy', 22 caracteres)
-- 2. tCurvaData: sai FK_tDadoCurva_tCurvaData (curva diária presa aos pontos),
--    entra FK_tCurvaMercd_tCurvaData (curva diária ligada à curva de mercado)
-- 3. tCurvaData: PK XPKtCurvaData nas mesmas colunas, agora CLUSTERED
-- ============================================================================

SET XACT_ABORT ON;
BEGIN TRANSACTION;

-- 1. Ticker Bloomberg completo
ALTER TABLE dbo.tBbergCurvaPrimr ALTER COLUMN cTickerBberg VARCHAR(50) NULL;

-- 2. Curva diária ligada à curva de mercado, não aos pontos
ALTER TABLE dbo.tCurvaData DROP CONSTRAINT FK_tDadoCurva_tCurvaData;
ALTER TABLE dbo.tCurvaData ADD CONSTRAINT FK_tCurvaMercd_tCurvaData
    FOREIGN KEY (cTickerIndcd) REFERENCES dbo.tCurvaMercd (cTickerIndcd);

-- 3. PK da curva diária clustered, mesmas colunas
ALTER TABLE dbo.tCurvaData DROP CONSTRAINT XPKtCurvaData;
ALTER TABLE dbo.tCurvaData ADD CONSTRAINT XPKtCurvaData
    PRIMARY KEY CLUSTERED (dBaseReft ASC, cTickerIndcd ASC, dVertcReft ASC);

COMMIT TRANSACTION;
GO

-- ============================================================================
-- Volta (só se precisar desfazer; descomentar e executar)
-- ============================================================================
-- SET XACT_ABORT ON;
-- BEGIN TRANSACTION;
-- ALTER TABLE dbo.tCurvaData DROP CONSTRAINT XPKtCurvaData;
-- ALTER TABLE dbo.tCurvaData ADD CONSTRAINT XPKtCurvaData
--     PRIMARY KEY NONCLUSTERED (dBaseReft ASC, cTickerIndcd ASC, dVertcReft ASC);
-- ALTER TABLE dbo.tCurvaData DROP CONSTRAINT FK_tCurvaMercd_tCurvaData;
-- ALTER TABLE dbo.tCurvaData ADD CONSTRAINT FK_tDadoCurva_tCurvaData
--     FOREIGN KEY (dBaseReft, cTickerIndcd, dVertcReft)
--     REFERENCES dbo.tDadoCurva (dBaseReft, cTickerIndcd, dVertcReft);
-- ALTER TABLE dbo.tBbergCurvaPrimr ALTER COLUMN cTickerBberg CHAR(20) NULL;
-- COMMIT TRANSACTION;
-- GO
