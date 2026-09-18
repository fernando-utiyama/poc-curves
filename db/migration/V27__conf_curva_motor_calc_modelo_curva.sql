-- ============================================================================
-- V27__conf_curva_motor_calc_modelo_curva.sql
-- ConstrucaoCurvaB3Service passou a despachar a construção das 5 curvas TS B3
-- para a mesma infraestrutura plugável de ModeloCurva/ModeloConstrucaoResolver
-- que antes só servia a curva DI1/BOOTSTRAPPED (removida junto com a limpeza
-- de BVBG.086/BVBG.028 no curve-processor) — cada curva pode agora apontar
-- para um modelo GROOVY customizado sem mudança de código, cadastrado via
-- POST /api/v1/modelos/validar-groovy, se a metodologia real de uma curva for
-- confirmada com a mesa. tConfgCurva.cMotorCalc passa a guardar o `codigo` de
-- um ModeloCurva (modelo_curva, schema antigo), não mais um identificador de
-- Interpolador direto (V26).
--
-- TAXA_SWAP_TRANSCRICAO_B3 é o modelo BUILTIN padrão (registrado em
-- ModeloCurvaBootstrap na subida do curve-engine, se ainda não existir) —
-- montagem direta dos vértices, sem recálculo, mesmo comportamento que V26
-- já tinha (o Manual de Curvas da B3 confirma que os valores em
-- tBtrsCurvaPrimr já são o resultado final do cálculo da B3).
-- ============================================================================

UPDATE [tConfgCurva]
SET cMotorCalc = 'TAXA_SWAP_TRANSCRICAO_B3'
WHERE cTickerIndcd IN (
    'B3_TAXA_SWAP_PRE',
    'B3_TAXA_SWAP_DCL',
    'B3_TAXA_SWAP_PTX',
    'B3_TAXA_SWAP_INP',
    'B3_TAXA_SWAP_DPL'
)
AND cMotorCalc = 'LINEAR';
GO
