-- ============================================================================
-- V26__curve_engine_schema_legado_taxa_swap.sql
-- Construção das 5 curvas TS B3 (PRE/DCL/PTX/INP/DPL) passa a rodar no
-- curve-engine, sobre o schema legado (V22) — leitura de tBtrsCurvaPrimr
-- (gravada pelo curve-processor) e tConfgCurva (motor de cálculo/interpolador
-- por curva), escrita em tDadoCurva (vértices construídos) e tCurvaData
-- (curva construída e interpolada). Ver openspec/changes/b3-additional-curves.
--
-- Exceção de DELETE em tDadoCurva/tCurvaData: mesma justificativa de
-- curve_processor_app em tBtrsCurvaPrimr (V23) — nenhuma das duas tem chave
-- natural além de (dBaseReft, cTickerIndcd, dVertcReft), então reprocessar
-- idempotentemente exige delete-then-insert, não upsert.
-- ============================================================================

GRANT SELECT ON tBtrsCurvaPrimr TO curve_engine_app;
GRANT SELECT ON tConfgCurva TO curve_engine_app;
GRANT SELECT ON tCurvaMercd TO curve_engine_app;
GRANT SELECT, INSERT, DELETE ON tDadoCurva TO curve_engine_app;
GRANT SELECT, INSERT, DELETE ON tCurvaData TO curve_engine_app;
GO

-- Motor de cálculo (interpolador) vigente por curva TS B3 — LINEAR para as 5,
-- de propósito: PTX (preço de câmbio) e INP (pontos de índice) não são taxa,
-- então FLAT_FORWARD (que assume fator de desconto (1+r)^(-t/252) sobre o
-- valor do vértice) não se aplica a elas; LINEAR interpola o valor bruto sem
-- nenhuma suposição de unidade/convenção, correto para as 5 mesmo não sendo
-- o método mais preciso para PRE/DCL/DPL (que são taxa de verdade). Ajustar
-- cMotorCalc por linha quando/se o método correto por curva for confirmado
-- com a mesa — o mecanismo (tConfgCurva.cMotorCalc -> InterpoladorRegistry)
-- já é por curva, essa migração só ainda não diferencia os valores.
INSERT INTO [tConfgCurva] (cTickerIndcd, cAtivoFincr, cMotorCalc, cTpoInstt, cVrsaoReg, dInicVgcia)
SELECT v.cTickerIndcd, 1, 'LINEAR', 'CURVA_SWAP', 1, '2020-01-01'
FROM (VALUES
    ('B3_TAXA_SWAP_PRE'),
    ('B3_TAXA_SWAP_DCL'),
    ('B3_TAXA_SWAP_PTX'),
    ('B3_TAXA_SWAP_INP'),
    ('B3_TAXA_SWAP_DPL')
) AS v(cTickerIndcd)
WHERE NOT EXISTS (SELECT 1 FROM [tConfgCurva] WHERE cTickerIndcd = v.cTickerIndcd);
GO
