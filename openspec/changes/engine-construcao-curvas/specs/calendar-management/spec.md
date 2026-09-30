## Purpose

Exporta, em JSON, os feriados dos calendários nativos (`Brazil`/`Settlement` e `UnitedStates`/`FederalReserve`), para o `services/curves` e o orquestrador conferirem dias úteis. A manutenção de feriados por planilha (calendário por lista, importação e exportação em planilha) está na change `engine-modelos-curva`.

## ADDED Requirements

### Requirement: Exportação dos feriados
`GET /api/v1/calendarios/{nome}?mercado=&anoInicial=&anoFinal=&formato=json` SHALL devolver os feriados do calendário no intervalo: os dias de segunda a sexta que não são úteis, com o nome, o mercado e a origem do calendário. O calendário SHALL ser resolvido como numa construção. O intervalo MUST ter no máximo 150 anos; senão, 400 `PARAMETRO_INVALIDO`. É a rota que o `services/curves` e o orquestrador usam para conferir dias úteis.

#### Scenario: Feriados do Brazil em 2026
- **WHEN** o cliente chama `GET /api/v1/calendarios/Brazil?mercado=Settlement&anoInicial=2026&anoFinal=2026&formato=json`
- **THEN** a resposta lista os feriados nacionais de 2026 que caem de segunda a sexta, inclusive `2026-11-20`

