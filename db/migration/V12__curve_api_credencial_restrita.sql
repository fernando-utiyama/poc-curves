-- ============================================================================
-- V12__curve_api_credencial_restrita.sql
-- Credencial restrita do curve-api (fecha o gap encontrado na auditoria desta
-- sessao: o servico rodava com a credencial sa, mesmo tratamento ja dado a
-- curve_processor_app em V8).
-- ============================================================================

CREATE LOGIN curve_api_app WITH PASSWORD = 'CurveApiP0c!Local', CHECK_POLICY = OFF;
CREATE USER curve_api_app FOR LOGIN curve_api_app;

-- Fronteira de escrita do servico: cadastro e versionamento de definicao de
-- curva (DefinicaoCurvaRepository/VersaoDefinicaoCurvaRepository) — as duas
-- unicas tabelas em que o codigo faz INSERT/UPDATE.
GRANT SELECT, INSERT, UPDATE ON definicao_curva TO curve_api_app;
GRANT SELECT, INSERT, UPDATE ON versao_definicao_curva TO curve_api_app;

-- Fronteira de leitura: consulta/comparacao de curvas publicadas
-- (CurvaConsultaRepository) e resolucao de modelo de calculo
-- (ModeloCurvaRepository) — sem permissao de escrita, essas tabelas
-- pertencem a outros servicos (curve-processor, curve-engine).
GRANT SELECT ON versao_curva TO curve_api_app;
GRANT SELECT ON vertice_curva TO curve_api_app;
GRANT SELECT ON procedencia_curva TO curve_api_app;
GRANT SELECT ON validacao_curva TO curve_api_app;
GRANT SELECT ON modelo_curva TO curve_api_app;
