-- ============================================================================
-- V8__curve_processor_ampliacoes.sql
-- Contador de divergencias em lote_ingestao e credencial restrita do curve-processor.
-- ============================================================================

-- Contagem acumulada de pontos regravados com valor diferente do anterior,
-- somada bloco a bloco (LoteIngestao.somarDivergencias) — alimenta o campo
-- "divergences" do evento marketdata.normalized.v1, emitido só apos o lote
-- consolidar (COMPLETO).
ALTER TABLE lote_ingestao
    ADD pontos_divergentes INT NOT NULL DEFAULT 0;

-- ----------------------------------------------------------------------------
-- Credencial restrita do curve-processor (tarefa 1.6 do backlog do servico).
-- Senha fixa documentada, so para o ambiente POC local — mesmo tratamento ja
-- dado a SA em application.yml (MSSQL_SA_PASSWORD). Nao usar em producao.
-- ----------------------------------------------------------------------------
CREATE LOGIN curve_processor_app WITH PASSWORD = 'CurveProcessorP0c!Local', CHECK_POLICY = OFF;
CREATE USER curve_processor_app FOR LOGIN curve_processor_app;

-- Fronteira de escrita do servico: lotes de ingestao, pontos de mercado, e as
-- tres tabelas de publicacao de curva pronta (secao 5 do backlog do servico:
-- ingestao de curva pronta da B3 e carga manual).
GRANT SELECT, INSERT, UPDATE ON lote_ingestao TO curve_processor_app;
GRANT SELECT, INSERT, UPDATE ON ponto_dado_mercado TO curve_processor_app;
GRANT SELECT, INSERT, UPDATE ON versao_curva TO curve_processor_app;
GRANT SELECT, INSERT, UPDATE ON vertice_curva TO curve_processor_app;
GRANT SELECT, INSERT, UPDATE ON procedencia_curva TO curve_processor_app;

-- Fronteira de leitura: resolucao de definicao de curva pelo identificador de
-- origem (tarefa 5.2) e vinculo com a execucao em andamento — sem permissao
-- de escrita, essas tabelas pertencem a outros servicos (curve-api, curve-orchestrator).
GRANT SELECT ON definicao_curva TO curve_processor_app;
GRANT SELECT ON versao_definicao_curva TO curve_processor_app;
GRANT SELECT ON execucao_curva TO curve_processor_app;
