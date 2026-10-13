-- ============================================================================
-- Tesouro: mais de uma publicação e mais de uma curva intradiária por dia
-- SQL Server, sobre o schema do 001_SCRIPT_INICIAL.sql. Tabelas sem dados.
--
-- 1. tTesouCurvaPrimr.dPublcc: horário local (Brasil) da publicação gravada
-- 2. tDadoVertcCurvaIntrd: irmã de tDadoVertcCurva com dHoraIntrd na PK, para
--    guardar a curva realtime calculada a cada 30 min (só do Tesouro)
-- ============================================================================

SET XACT_ABORT ON;
BEGIN TRANSACTION;

-- 1. Publicação da bruta
ALTER TABLE dbo.tTesouCurvaPrimr ADD dPublcc datetime NULL;

-- 2. Vértices construídos intradiários (curva realtime)
CREATE TABLE dbo.tDadoVertcCurvaIntrd
(
    dBaseReft     date           NOT NULL,
    cTickerIndcd  varchar(50)    NOT NULL,
    dHoraIntrd    datetime       NOT NULL,
    dVertcReft    date           NOT NULL,
    cDiaUtil      int            NULL,
    vFatorDia     DECIMAL(28,16) NULL,
    vFatorAcum    DECIMAL(28,16) NULL,
    cQtdDiaPer    int            NULL,
    cQtdDiaReft   int            NULL,
    vPrecoTx      DECIMAL(28,12) NULL
);

ALTER TABLE dbo.tDadoVertcCurvaIntrd ADD CONSTRAINT XPKtDadoVertcCurvaIntrd
    PRIMARY KEY NONCLUSTERED (dBaseReft ASC, cTickerIndcd ASC, dHoraIntrd ASC, dVertcReft ASC);

ALTER TABLE dbo.tDadoVertcCurvaIntrd ADD CONSTRAINT FK_tCurvaMercd_tDadoVertcCurvaIntrd
    FOREIGN KEY (cTickerIndcd) REFERENCES dbo.tCurvaMercd (cTickerIndcd);

COMMIT TRANSACTION;
GO
