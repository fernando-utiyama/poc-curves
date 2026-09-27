-- ============================================================================
-- Ajustes de schema das curvas de mercado (change banco-curvas-ajustes)
-- SQL Server. Banco ainda sem uso: tabelas vazias, sem migração de dados.
--
-- 1. tBbergCurvaPrimr.cTickerBberg: CHAR(20) -> VARCHAR(50)
--    (ticker Bloomberg completo, ex.: 'S0490Z 15M BLC2 Curncy', 22 caracteres)
-- 2. Sequência para tBbergCurvaPrimr.cldtfdUnic (INT NOT NULL, sem identity)
-- 3. tCurvaData: sai FK_tDadoCurva_tCurvaData (curva diária presa aos pontos),
--    entra FK_tCurvaMercd_tCurvaData (curva diária ligada à curva de mercado)
-- 4. tCurvaData: PK XPKtCurvaData nas mesmas colunas, agora CLUSTERED
-- ============================================================================

SET XACT_ABORT ON;
BEGIN TRANSACTION;

-- 1. Ticker Bloomberg completo
ALTER TABLE dbo.tBbergCurvaPrimr ALTER COLUMN cTickerBberg VARCHAR(50) NULL;

-- 2. Sequência do id do bruto Bloomberg
CREATE SEQUENCE dbo.seq_tbbergcurvaprimr_cidtfdunic AS INT START WITH 1 INCREMENT BY 1;

-- 3. Curva diária ligada à curva de mercado, não aos pontos
ALTER TABLE dbo.tCurvaData DROP CONSTRAINT FK_tDadoCurva_tCurvaData;
ALTER TABLE dbo.tCurvaData ADD CONSTRAINT FK_tCurvaMercd_tCurvaData
    FOREIGN KEY (cTickerIndcd) REFERENCES dbo.tCurvaMercd (cTickerIndcd);

-- 4. PK da curva diária clustered, mesmas colunas
ALTER TABLE dbo.tCurvaData DROP CONSTRAINT XPKtCurvaData;
ALTER TABLE dbo.tCurvaData ADD CONSTRAINT XPKtCurvaData
    PRIMARY KEY CLUSTERED (dBaseReft ASC, cTickerIndcd ASC, dVertcReft ASC);

COMMIT TRANSACTION;
GO

-- 5. Permissão de uso da sequência para quem grava tBbergCurvaPrimr
--    (NEXT VALUE FOR exige UPDATE na sequência). Trocar pelo login do gravador Bloomberg:
-- GRANT UPDATE ON OBJECT::dbo.seq_tbbergcurvaprimr_cidtfdunic TO [<login_gravador_bloomberg>];
-- GO

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
-- DROP SEQUENCE dbo.seq_tbbergcurvaprimr_cidtfdunic;
-- ALTER TABLE dbo.tBbergCurvaPrimr ALTER COLUMN cTickerBberg CHAR(20) NULL;
-- COMMIT TRANSACTION;
-- GO
