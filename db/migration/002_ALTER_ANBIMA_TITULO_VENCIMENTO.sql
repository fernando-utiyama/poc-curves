-- ============================================================================
-- tAnbmaCurvaPrimr: tipo do título e vencimento
-- SQL Server, sobre o schema do 001_SCRIPT_INICIAL.sql. Tabela sem dados.
--
-- cTpoInstt:  tipo do título (NTN-B, LTN, NTN-F, LFT)
-- dVctoContr: vencimento exato do título (arquivo ms da ANBIMA)
-- Ambas NULL: as linhas da CurvaZero não têm título.
-- ============================================================================

SET XACT_ABORT ON;
BEGIN TRANSACTION;

ALTER TABLE dbo.tAnbmaCurvaPrimr ADD
    cTpoInstt   varchar(50) NULL,
    dVctoContr  date        NULL;

COMMIT TRANSACTION;
GO
