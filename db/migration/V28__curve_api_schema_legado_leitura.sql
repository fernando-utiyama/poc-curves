-- ============================================================================
-- V28__curve_api_schema_legado_leitura.sql
-- curve-api migra o catálogo/consulta de curvas para o schema legado (V22) —
-- definicao_curva/versao_curva/vertice_curva/procedencia_curva (curva DI1/
-- BOOTSTRAPPED, removida do curve-processor/curve-engine) saem de uso;
-- tCurvaMercd (catálogo), tDadoCurva (vértices construídos) e tCurvaData
-- (curva construída e interpolada) passam a ser a única fonte. Só leitura —
-- essas tabelas pertencem a curve-processor (tCurvaMercd, populada por
-- migração) e curve-engine (tDadoCurva/tCurvaData, escritas por
-- ConstrucaoCurvaB3Service).
-- ============================================================================

GRANT SELECT ON tCurvaMercd TO curve_api_app;
GRANT SELECT ON tDadoCurva TO curve_api_app;
GRANT SELECT ON tCurvaData TO curve_api_app;
GO
