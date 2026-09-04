-- ============================================================================
-- V18__curve_engine_credencial_restrita.sql
-- Credencial restrita do curve-engine (tarefa 5.3 do backlog curve-engine),
-- mesmo tratamento ja dado a curve_processor_app (V8), curve_api_app (V12) e
-- curve_orchestrator_app (V13) — o servico rodava com a credencial sa,
-- documentada como provisoria em application.yml desde o esqueleto inicial.
-- ============================================================================

CREATE LOGIN curve_engine_app WITH PASSWORD = 'CurveEngineP0c!Local', CHECK_POLICY = OFF;
CREATE USER curve_engine_app FOR LOGIN curve_engine_app;

-- Fronteira de escrita do servico: publicacao de curva (versao, vertices,
-- procedencia), resultado da bateria de validacao, e catalogo de modelos de
-- construcao (cadastro/importacao/habilitacao de modelo embutido e Groovy).
GRANT SELECT, INSERT, UPDATE ON versao_curva TO curve_engine_app;
GRANT SELECT, INSERT, UPDATE ON vertice_curva TO curve_engine_app;
GRANT SELECT, INSERT, UPDATE ON procedencia_curva TO curve_engine_app;
GRANT SELECT, INSERT, UPDATE ON validacao_curva TO curve_engine_app;
GRANT SELECT, INSERT, UPDATE ON modelo_curva TO curve_engine_app;

-- Fronteira de leitura: resolucao de definicao/versao de definicao vigente
-- (para saber qual modelo usar e quais insumos exigir) e leitura dos insumos
-- de mercado ja ingeridos — sem permissao de escrita, essas tabelas
-- pertencem a curve-api (definicao_curva/versao_definicao_curva) e a
-- curve-processor (ponto_dado_mercado).
GRANT SELECT ON definicao_curva TO curve_engine_app;
GRANT SELECT ON versao_definicao_curva TO curve_engine_app;
GRANT SELECT ON ponto_dado_mercado TO curve_engine_app;
