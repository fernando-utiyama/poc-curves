-- ============================================================================
-- V23__curva_mercd_taxa_swap_e_sequence_btrs.sql
-- Primeiro passo da adaptação do curve-processor para o schema legado
-- (V22): as curvas TS B3 (DCL/PTX/INP/DPL) passam a gravar em
-- tBtrsCurvaPrimr em vez de ponto_dado_mercado/versao_curva (decisão
-- confirmada com o usuário — substitui só para TS B3, PR/IN/B3_CURVA_PRE
-- continuam no pipeline atual sem mudança).
--
-- Duas peças de infraestrutura que faltavam no schema replicado (V22) para
-- isso funcionar:
--
-- 1. SEQUENCE para cldtfdUnic: a coluna em tBtrsCurvaPrimr (e nas demais
--    tabelas "*CurvaPrimr") é `int NOT NULL` sem IDENTITY — a aplicação
--    precisa gerar esse valor sozinha. Sem o código de carga original
--    (BloombergCurvaPrimr.java) disponível para confirmar a convenção real,
--    usamos uma SEQUENCE dedicada (geração de id "padrão" para coluna não-
--    identity em aplicação Spring/JDBC, sem mudar o tipo da coluna nem violar
--    a réplica do schema original) — decisão confirmada com o usuário.
--    Escopada só para tBtrsCurvaPrimr por ora; as demais tabelas
--    "*CurvaPrimr" ganham a sua quando forem adaptadas.
--
-- 2. Linhas em tCurvaMercd para as quatro curvas alvo: tBtrsCurvaPrimr tem
--    FK obrigatória (cTickerIndcd -> tCurvaMercd.cTickerIndcd) — sem a linha
--    pai, o INSERT falha. cTickerIndcd usa o mesmo código já usado em todo o
--    resto da plataforma para essas curvas (nome do dataset Kafka:
--    B3_TAXA_SWAP_DCL/PTX/INP/DPL), para manter um único identificador em
--    todo o sistema. PRE não entra aqui — o oráculo cruzado de PRE
--    (openspec/changes/b3-additional-curves, tarefa 5) não se aplica mais a
--    este caminho novo (a decisão de "substituir" removeu esse gate).
-- ============================================================================

IF NOT EXISTS (SELECT * FROM sys.sequences WHERE name = 'seq_tbtrscurvaprimr_cidtfdunic')
    CREATE SEQUENCE seq_tbtrscurvaprimr_cidtfdunic AS INT START WITH 1 INCREMENT BY 1;
GO

INSERT INTO [tCurvaMercd] (cTickerIndcd, cClasfInstt, cClassAtivo, cMoedaNegoc, dInicVgcia, cUsuarCalc)
SELECT v.cTickerIndcd, 'CURVA_SWAP', 'TAXA_JUROS', 'BRL', CAST(SYSUTCDATETIME() AS date), 'migration-V23'
FROM (VALUES
    ('B3_TAXA_SWAP_DCL'),
    ('B3_TAXA_SWAP_PTX'),
    ('B3_TAXA_SWAP_INP'),
    ('B3_TAXA_SWAP_DPL')
) AS v(cTickerIndcd)
WHERE NOT EXISTS (SELECT 1 FROM [tCurvaMercd] WHERE [tCurvaMercd].cTickerIndcd = v.cTickerIndcd);
GO

-- ----------------------------------------------------------------------------
-- Credencial restrita do curve-processor (V8): ganha acesso a tBtrsCurvaPrimr.
-- Única exceção deliberada à política "nunca DELETE" (V8's comentário) — a
-- tabela legada não tem chave natural (cldtfdUnic é um id arbitrário de
-- sequence, não (curva, data, prazo)), então o padrão idempotente de
-- reprocessamento (substituir, não acumular) exige DELETE. REFERENCES em
-- tCurvaMercd é necessário para o INSERT em tBtrsCurvaPrimr respeitar a FK
-- (cTickerIndcd) sob a credencial restrita, não só sob dbo/sa.
-- ----------------------------------------------------------------------------
GRANT SELECT, INSERT, DELETE ON tBtrsCurvaPrimr TO curve_processor_app;
GRANT SELECT, REFERENCES ON tCurvaMercd TO curve_processor_app;
-- NEXT VALUE FOR exige permissão UPDATE na sequence (não SELECT) no SQL Server.
GRANT UPDATE ON seq_tbtrscurvaprimr_cidtfdunic TO curve_processor_app;
GO
