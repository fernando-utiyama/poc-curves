-- ============================================================================
-- V21__b3_taxa_swap_curvas_importadas.sql
-- Cadastro das quatro novas curvas importadas prontas da B3, extraidas do
-- arquivo "Mercado de Derivativos - Taxas de Mercado para Swaps" (TaxaSwap.txt,
-- openspec/changes/b3-additional-curves). O codigo de cada definicao_curva e
-- literalmente o nome do dataset que o feeder publica (mesma convencao ja
-- usada por B3_CURVA_PRE) -- ProcessarEnvelopeIngestaoUseCase.publicarCurvaImportadaSePossivel
-- resolve a definicao pelo dataset do envelope, entao os dois tem que
-- coincidir. PRE nao ganha definicao nova aqui: e so o oraculo de validacao
-- cruzada (tarefa 5 do backlog), e a curva PRE oficial da B3 ja existe via
-- B3_CURVA_PRE (referenceRatesProxy).
--
-- Campos de construcao (contagem_dias, calendario, interpolador, politica_*)
-- nao tem efeito nenhum para curvas IMPORTED -- ninguem interpola nem
-- reconstroi um vertice importado -- mas a coluna e NOT NULL (V1), entao
-- precisam de um valor coerente com os conjuntos validos de
-- DefinicaoCoerenciaValidator (curve-api), para o caso de alguem editar esta
-- definicao depois pela API sem trombar em "valor invalido". politica_arredondamento
-- usa TRUNCATE_8 para as quatro: o layout do TaxaSwap.txt ja chega com 7 casas
-- decimais implicitas e o parser nao arredonda na ingestao (mesmo padrao do
-- B3TaxaSwapParser/B3CurvaProntaParser) -- o Manual da B3 usa casas de exibicao
-- diferentes por curva (DCL/DPL 2, PTX 7 truncado, INP 2), mas o schema atual
-- de definicao_curva nao modela uma politica de arredondamento por tipo de
-- valor, so a politica generica ja existente; refinar isso fica para quando
-- houver necessidade real declarada de exibir cada curva na casa oficial do
-- Manual.
-- ============================================================================

DECLARE @agora DATETIME2 = SYSUTCDATETIME();

INSERT INTO definicao_curva (id, codigo, nome, moeda, modo_origem, horario_limite_publicacao, estado, criado_em, criado_por)
VALUES
    (NEWID(), 'B3_TAXA_SWAP_DCL', 'Cupom Cambial Limpo (B3, TaxaSwap.txt)', 'BRL', 'IMPORTED', '18:00', 'ATIVA', @agora, 'migration-V21'),
    (NEWID(), 'B3_TAXA_SWAP_PTX', 'PTAX - Cambio R$/US$ (B3, TaxaSwap.txt)', 'BRL', 'IMPORTED', '18:00', 'ATIVA', @agora, 'migration-V21'),
    (NEWID(), 'B3_TAXA_SWAP_INP', 'Ibovespa - Pontos de Indice (B3, TaxaSwap.txt)', 'BRL', 'IMPORTED', '18:00', 'ATIVA', @agora, 'migration-V21'),
    (NEWID(), 'B3_TAXA_SWAP_DPL', 'Cupom Limpo de IPCA D+1 (B3, TaxaSwap.txt)', 'BRL', 'IMPORTED', '18:00', 'ATIVA', @agora, 'migration-V21');

INSERT INTO versao_definicao_curva (
    id, definicao_curva_id, numero_versao, contagem_dias, calendario,
    interpolador, politica_extrapolacao, politica_arredondamento, vigencia_inicio, vigencia_fim
)
SELECT
    NEWID(), dc.id, 1, 'DU/252', 'ANBIMA', 'LINEAR', 'STRICT', 'TRUNCATE_8', '2020-01-01', NULL
FROM definicao_curva dc
WHERE dc.codigo IN ('B3_TAXA_SWAP_DCL', 'B3_TAXA_SWAP_PTX', 'B3_TAXA_SWAP_INP', 'B3_TAXA_SWAP_DPL')
  AND dc.criado_por = 'migration-V21';
