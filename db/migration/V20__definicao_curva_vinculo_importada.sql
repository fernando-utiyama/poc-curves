-- ============================================================================
-- V20__definicao_curva_vinculo_importada.sql
-- Vinculo (referencia leve, sem FK) de uma definicao_curva BOOTSTRAPPED para
-- o codigo da sua definicao_curva IMPORTED irma, quando existir. Referencia
-- por codigo (nao por id/FK) porque a definicao IMPORTED pode ainda nao
-- existir no momento em que a BOOTSTRAPPED e cadastrada, e o inverso tambem
-- e verdade -- nao ha ordem de criacao garantida entre as duas.
-- Resolve o gap documentado em PublicacaoCurvaService.java (curve-engine):
-- "nao existe hoje nenhum mecanismo de vincular uma definicao BOOTSTRAPPED
-- a uma definicao IMPORTED irma da mesma curva".
-- ============================================================================

ALTER TABLE definicao_curva
    ADD codigo_curva_importada_irmao NVARCHAR(50) NULL;
