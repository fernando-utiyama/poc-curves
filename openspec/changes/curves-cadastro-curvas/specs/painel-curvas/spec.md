## Purpose

No `services/curves`, dar ao gestor uma tela de acompanhamento das curvas: uma linha por curva, numa data-base, com a última publicação, a situação da curva naquela data e o que precisa de atenção. O painel junta o que o curves já tem (cadastro e pontos gravados) com a conferência que o engine faz na hora, contra a fonte atual. Nenhum dos dois guarda estado para o painel: tudo é calculado a cada consulta.

## ADDED Requirements

### Requirement: Rota do painel
O serviço SHALL expor `GET /api/v1/curvas-mercado/painel?dataBase=&situacao=&provedor=&nome=&somenteAtencao=` (papel `Curvas.Leitura`), com:
- `dataBase`: opcional. Sem ela, SHALL ser hoje (horário de Brasília) se for dia útil no `Brazil`/`Settlement`, senão o dia útil anterior. Com o engine fora, sábado e domingo recuam para sexta-feira, sem conferir feriados, e a regra `NAO_E_DIA_UTIL` só considera sábado e domingo;
- `situacao`: filtra por uma ou mais situações (repetível);
- `provedor`: filtra pelo provedor da origem;
- `nome`: trecho do nome, normalizado como nas demais rotas;
- `somenteAtencao=true`: só as linhas com `atencao` = `true`.

A resposta SHALL trazer a data-base usada, o instante da consulta, os contadores (quantidade de curvas por situação e quantas com `atencao`, sempre sobre todas as curvas listáveis, antes dos filtros), os avisos gerais e as linhas, ordenadas por código. Entram no painel as curvas com código não nulo, ativas ou não.

#### Scenario: Painel do fim do dia
- **WHEN** o gestor abre o painel às 20h30 de `2026-09-14`, depois da carga B3, com a `DPL` falhando e sem carga da ANBIMA nem da Bloomberg
- **THEN** a data-base é `2026-09-14`, os contadores mostram 4 curvas `CONSTRUIDA`, 1 `COM_ERRO` e 2 `AGUARDANDO_CARGA` (`NTNB` e `SOFR`), e as linhas vêm ordenadas por código

### Requirement: Colunas de cada linha
Cada linha SHALL trazer:

| Campo | Fonte |
|---|---|
| `codigo`, `nome`, `unidade`, `situacaoCadastro` (`ATIVO`/`INATIVO`) | `tCurvaMercd` |
| `origem` (provedor, produto, código na fonte; ou as mães, para curva derivada) | `tCurvaPrvdr`, ligação de menor prioridade |
| `origensSecundarias` (provedor, produto, código na fonte, prioridade e o modelo que a lê, de `MODELOS_POR_ORIGEM` ou `modeloConstrucao`), lista vazia quando não há | `tCurvaPrvdr` e configuração vigente |
| `modeloConstrucao`, `interpolador`, `versaoConfiguracao` | configuração vigente na data-base em `tConfgCurva` |
| `ultimaDataPublicada`, `calculadoPor` | `tCurvaMercd.dBaseReft` e `cUsuarCalc` |
| `situacao`, `motivo`, `atencao`, `atrasada` | regra abaixo |
| `quantidadePontos`, `hashPontos` | pontos gravados em `tDadoVertcCurva` na data-base, com o `hashPontos` da spec `pontos-curva-manual` |
| `interpolada` (quantidade de linhas em `tDadoCurva` e se confere com os pontos atuais) | engine |
| `insumo` (linhas brutas da origem na data, ou mães com e sem pontos) | engine |
| `conferencia` (`status`, `codigoErro`, mensagem, `pontosDiferentes`) | engine, calculada na hora contra a fonte atual |

Campos sem informação SHALL vir nulos, nunca omitidos. Com `origensSecundarias`, o front SHALL oferecer, na ação de construir ou recalcular a curva, a escolha entre a origem principal e cada secundária, chamando a construção do engine com `fonte` e `produto` da escolhida, e a simulação por ela antes de gravar. Para ver o detalhe de uma linha, o front pede o arquivo de auditoria da curva e data ao engine (spec `curve-audit-history` do change `engine-modelos-curva`), montado na hora.

#### Scenario: Curva com erro
- **WHEN** a construção da `DPL` de `2026-09-14` falhou por `INSUMO_INVALIDO` na carga
- **THEN** a linha da `DPL` tem `situacao` = `COM_ERRO`, `atencao` = `true`, `quantidadePontos` = 0, linhas brutas na data e `conferencia` com `INSUMO_INVALIDO` e a mensagem

### Requirement: Situação na data-base
A situação SHALL ser decidida nesta ordem, pela primeira regra que se aplica:

| Situação | Regra | `atencao` |
|---|---|---|
| `NAO_E_DIA_UTIL` | a data-base não é dia útil no calendário da configuração vigente da curva, e não há pontos gravados | não |
| `IGNORADA` | curva `INATIVO` ou data-base fora da vigência da curva, e não há pontos gravados | não |
| `SITUACAO_INDISPONIVEL` | o engine não respondeu; `quantidadePontos` mostra se há pontos gravados | sim |
| `INTERPOLADA_DESATUALIZADA` | há pontos gravados, mas a curva interpolada em `tDadoCurva` não confere com eles (edição manual com o engine fora); `motivo` traz a quantidade de dias diferentes | sim |
| `CONSTRUIDA` | há pontos gravados, e a conferência com a fonte atual está `OK` com 0 pontos diferentes | não |
| `DIVERGENTE_DA_FONTE` | há pontos gravados, mas eles não batem com a fonte atual; `motivo`: `PONTOS_DIFERENTES` (edição manual, republicação ou cadastro alterado, com a quantidade), `FONTE_COM_ERRO` (a fonte atual não gera a curva, com o código) ou `SEM_INSUMO` (pontos digitados sem dado da fonte, ou mães sem pontos) | sim |
| `AGUARDANDO_MAES` | curva derivada sem pontos gravados, com alguma mãe ainda sem pontos na data | não |
| `AGUARDANDO_CARGA` | curva com origem de provedor, sem pontos gravados e sem linhas brutas na data | só se `atrasada` |
| `COM_ERRO` | sem pontos gravados, com insumo, e a conferência dá erro (é o erro que a construção dá) | sim |
| `NAO_CONSTRUIDA` | sem pontos gravados, com insumo, e a conferência está `OK`: a curva poderia ser construída e não foi | sim |

`atrasada` SHALL ser `true` quando a situação for `AGUARDANDO_CARGA` ou `NAO_CONSTRUIDA`, a data-base for hoje, e o horário atual (Brasília) tiver passado do horário esperado do provedor da origem, configurado em `curves.painel.horario-esperado.{provedor}` (ex.: `curves.painel.horario-esperado.B3=20:00`). Para data-base passada, `AGUARDANDO_CARGA` e `NAO_CONSTRUIDA` são sempre `atrasada`. Sem horário configurado para o provedor, `atrasada` é `false` na data de hoje.

#### Scenario: Curva editada à mão
- **WHEN** a `PRE` de `2026-09-14` foi construída pelo engine e depois teve um ponto alterado no `services/curves`
- **THEN** a linha da `PRE` tem `situacao` = `DIVERGENTE_DA_FONTE`, `motivo` = `PONTOS_DIFERENTES`, `conferencia.pontosDiferentes` = 1 e `atencao` = `true`

#### Scenario: Data construída pela origem secundária
- **WHEN** a `DI_BACKUP` de `2026-09-14` foi construída pela reserva `B3`/`TS`, e a origem principal `ANBIMA`/`CZ` carregou depois com valores diferentes
- **THEN** a linha tem `situacao` = `DIVERGENTE_DA_FONTE`, `motivo` = `PONTOS_DIFERENTES`, porque a conferência é sempre contra a principal, e `origensSecundarias` lista a `B3`/`TS` com o modelo `PRONTA_TS_B3`

#### Scenario: Republicação sem recálculo
- **WHEN** a B3 republicou o arquivo de `2026-09-14` com um vértice da `DCL` corrigido depois da construção, e ninguém recalculou
- **THEN** a `DCL` aparece como `DIVERGENTE_DA_FONTE`, com `motivo` = `PONTOS_DIFERENTES` e 1 ponto diferente, e as curvas B3 sem mudança continuam `CONSTRUIDA`

#### Scenario: Derivada com mãe recalculada
- **WHEN** a `DIxPRE` de uma data foi recalculada com valores diferentes depois da construção de uma curva derivada dela
- **THEN** a derivada aparece como `DIVERGENTE_DA_FONTE`, com `motivo` = `PONTOS_DIFERENTES`

#### Scenario: Carga atrasada
- **WHEN** às 20h30 de hoje a SOFR ainda não tem linhas brutas, e o horário esperado da `BLOOMBERG` é `19:00`
- **THEN** a linha da `SOFR` tem `situacao` = `AGUARDANDO_CARGA`, `atrasada` = `true` e `atencao` = `true`

#### Scenario: Feriado americano
- **WHEN** a data-base é `2026-11-26` (Thanksgiving, dia útil no Brasil)
- **THEN** a `SOFR` aparece como `NAO_E_DIA_UTIL`, sem `atencao`, e as curvas B3 seguem as demais regras

### Requirement: Dados do engine sem bloquear o painel
O serviço SHALL obter o insumo e a conferência por `GET /api/v1/curvas/situacao?dataBase=` do engine (spec `curve-engine-api` do change `engine-modelos-curva`), calculados na hora, e os calendários pela exportação de calendário do engine, com token de serviço, numa chamada de cada por consulta do painel, com tempo limite de 60 segundos para a situação e 10 segundos para o calendário. O painel MUST NOT falhar por causa do engine: se ele não responder, as linhas SHALL vir com o que o curves tem (cadastro, última data publicada, pontos gravados), `situacao` = `SITUACAO_INDISPONIVEL`, e o aviso geral `ENGINE_INDISPONIVEL`.

#### Scenario: Engine fora
- **WHEN** o engine não responde e o gestor abre o painel
- **THEN** a resposta é 200 com todas as curvas, a última data publicada e a quantidade de pontos de cada uma, `situacao` = `SITUACAO_INDISPONIVEL` e o aviso `ENGINE_INDISPONIVEL`
