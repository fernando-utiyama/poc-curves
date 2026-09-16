-- ============================================================================
-- V24__curva_mercd_taxa_swap_pre.sql
-- PRE (DIxPRE) do TaxaSwap.txt também passa a gravar em tBtrsCurvaPrimr —
-- faltava na primeira rodada (V23), que só cadastrou DCL/PTX/INP/DPL porque
-- PRE só era usado pelo oráculo cruzado que o caminho novo substituiu.
-- Correção: PRE é uma curva do arquivo como qualquer outra, precisa da
-- mesma linha em tCurvaMercd (FK obrigatória de tBtrsCurvaPrimr) que as
-- demais quatro (V23).
-- ============================================================================

INSERT INTO [tCurvaMercd] (cTickerIndcd, cClasfInstt, cClassAtivo, cMoedaNegoc, dInicVgcia, cUsuarCalc)
SELECT 'B3_TAXA_SWAP_PRE', 'CURVA_SWAP', 'TAXA_JUROS', 'BRL', CAST(SYSUTCDATETIME() AS date), 'migration-V24'
WHERE NOT EXISTS (SELECT 1 FROM [tCurvaMercd] WHERE cTickerIndcd = 'B3_TAXA_SWAP_PRE');
GO
