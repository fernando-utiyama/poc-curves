-- ============================================================================
-- V19__versao_curva_construcao_concorrente.sql
-- Guarda de construcao concorrente (tarefa 6.8 do backlog curve-engine): duas
-- construcoes da mesma curva/data/momento simultaneas nao podem, ambas,
-- inserir uma versao EM_VALIDACAO -- apenas uma prossegue, a outra e
-- reconhecida como redundante (ver curve-construction/spec.md, cenario
-- "Construcao concorrente da mesma curva"). Mesmo padrao ja usado pelo
-- indice ux_versao_curva_publicada (V4), so que para o estado EM_VALIDACAO
-- em vez de PUBLICADA -- REPROVADA/SUBSTITUIDA sao historico legitimo e nao
-- devem bloquear uma tentativa nova.
-- ============================================================================

CREATE UNIQUE INDEX ux_versao_curva_em_validacao
    ON versao_curva (definicao_curva_id, data_referencia, momento_curva)
    WHERE estado = 'EM_VALIDACAO';
