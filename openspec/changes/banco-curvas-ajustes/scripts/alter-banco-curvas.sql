-- ============================================================================
-- Ajustes de schema das curvas de mercado (change banco-curvas-ajustes)
-- SQL Server, sobre o schema real (o do 001_SCRIPT_INICIAL.sql). Banco ainda sem uso: tabelas
-- vazias, sem migração de dados.
--
-- 1. tBbergCurvaPrimr.cTickerBberg: CHAR(20) -> VARCHAR(50)
--    (ticker Bloomberg completo, ex.: 'S0490Z 15M BLC2 Curncy', 22 caracteres)
-- 2. tCurvaData: tabela removida, com a FK_tDadoCurva_tCurvaData
--    (a curva interpolada fica em tDadoCurva, e a construída em tDadoVertcCurva)
-- ============================================================================

SET XACT_ABORT ON;
BEGIN TRANSACTION;

-- 1. Ticker Bloomberg completo
ALTER TABLE dbo.tBbergCurvaPrimr ALTER COLUMN cTickerBberg VARCHAR(50) NULL;

-- 2. tCurvaData sai (a FK dela para tDadoCurva sai junto)
ALTER TABLE dbo.tCurvaData DROP CONSTRAINT FK_tDadoCurva_tCurvaData;
DROP TABLE dbo.tCurvaData;

COMMIT TRANSACTION;
GO

-- ============================================================================
-- Volta (só se precisar desfazer; descomentar e executar)
-- Recria tCurvaData exatamente como no 001_SCRIPT_INICIAL.sql.
-- ============================================================================
-- SET XACT_ABORT ON;
-- BEGIN TRANSACTION;
-- CREATE TABLE dbo.tCurvaData
-- (
--     dBaseReft     date           NOT NULL,
--     cTickerIndcd  varchar(50)    NOT NULL,
--     dVertcReft    date           NOT NULL,
--     vPrecoTx      DECIMAL(28,12) NULL
-- );
-- ALTER TABLE dbo.tCurvaData ADD CONSTRAINT XPKtCurvaData
--     PRIMARY KEY NONCLUSTERED (dBaseReft ASC, cTickerIndcd ASC, dVertcReft ASC);
-- ALTER TABLE dbo.tCurvaData ADD CONSTRAINT FK_tDadoCurva_tCurvaData
--     FOREIGN KEY (dBaseReft, cTickerIndcd, dVertcReft)
--     REFERENCES dbo.tDadoCurva (dBaseReft, cTickerIndcd, dVertcReft);
-- ALTER TABLE dbo.tBbergCurvaPrimr ALTER COLUMN cTickerBberg CHAR(20) NULL;
-- COMMIT TRANSACTION;
-- GO
