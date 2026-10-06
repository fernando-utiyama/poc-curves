## ADDED Requirements

### Requirement: Situação na resposta da construção
A resposta de `POST /api/v1/curvas/{codigo}/{dataBase}/construcao` SHALL trazer o campo `situacao` em todos os resultados, com o valor do resultado: `CONSTRUIDA`, `RECONSTRUIDA`, `EXISTENTE` e, para os resultados que já existem além desses, o nome correspondente (`IGNORADA`, `SEM_INSUMO`, `FALHOU`). Os demais campos de cada resultado continuam como estão.

#### Scenario: Recálculo
- **WHEN** a `PRE` de `2026-09-14`, já construída, é construída com `forcarRecalculo=true`
- **THEN** a resposta traz `situacao` = `RECONSTRUIDA`

#### Scenario: Curva já existente
- **WHEN** a `PRE` de `2026-09-14`, já construída, é construída sem `forcarRecalculo`
- **THEN** a resposta traz `situacao` = `EXISTENTE`

### Requirement: Dias úteis de vértice sem dias úteis informados
Com eixo de tempo em dias úteis (`Business252`), um vértice sem dias úteis publicados ou informados SHALL ter os dias úteis calculados pelo calendário da curva entre a data-base e a data do vértice, e MUST NOT ser tratado como prazo 0 nem descartado por isso.

#### Scenario: Vértice sem dias úteis informados
- **WHEN** uma curva com eixo `Business252` é construída com um vértice em `2027-05-17` sem dias úteis informados, data-base `2026-09-28`
- **THEN** o vértice tem os dias úteis do calendário entre `2026-09-28` e `2027-05-17` e não é descartado
