-- ============================================================================
-- V25__indice_tbtrscurvaprimr_cticker_dbasereft.sql
-- tBtrsCurvaPrimr (V22) ficou sem o índice em cTickerIndcd que toda outra
-- tabela "*CurvaPrimr" replicada ganhou (XIF1t*CurvaPrimr) — achado do
-- /simplify: BtrsCurvaPrimrRepository.substituirVertices roda
-- `DELETE ... WHERE cTickerIndcd = ? AND dBaseReft = ?` a cada ingestão, sem
-- índice isso é table scan completo toda vez (curva/data, inclusive em
-- reentrega do Kafka). PK é cldtfdUnic (id de sequence, sem relação com
-- curva/data), não ajuda essa busca.
-- ============================================================================

CREATE NONCLUSTERED INDEX [XIF1tBtrsCurvaPrimr] ON [tBtrsCurvaPrimr]
(
    [cTickerIndcd] ASC,
    [dBaseReft] ASC
)
GO
