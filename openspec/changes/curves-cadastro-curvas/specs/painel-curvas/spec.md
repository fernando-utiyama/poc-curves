## Purpose

No `services/curves`, dar ao gestor uma tela de acompanhamento das curvas: uma linha por curva, numa data-base, com a última publicação, a situação da curva naquela data e o que precisa de atenção. O painel junta o que o curves já tem (cadastro e pontos gravados) com a situação das construções, que só o engine conhece e expõe por uma rota de leitura. O curves nunca lê os arquivos internos do engine.

## ADDED Requirements

### Requirement: Rota do painel
O serviço SHALL expor `GET /api/v1/curvas-mercado/painel?dataBase=&situacao=&provedor=&nome=&somenteAtencao=` (papel `Curvas.Leitura`), com:
- `dataBase`: opcional. Sem ela, SHALL ser hoje (horário de Brasília) se for dia útil no `Brazil`/`Settlement`, senão o dia útil anterior. Com o engine fora, sábado e domingo recuam para sexta-feira, sem conferir feriados;
- `situacao`: filtra por uma ou mais situações (repetível);
- `provedor`: filtra pelo provedor da origem;
- `nome`: trecho do nome, normalizado como nas demais rotas;
- `somenteAtencao=true`: só as linhas com `atencao` = `true`.

A resposta SHALL trazer a data-base usada, o instante da consulta, os contadores (quantidade de curvas por situação e quantas com `atencao`, sempre sobre todas as curvas listáveis, antes dos filtros), os avisos gerais e as linhas, ordenadas por código. Entram no painel as curvas com código não nulo, ativas ou não.

#### Scenario: Painel do fim do dia
- **WHEN** o gestor abre o painel às 20h30 de `2026-09-14`, depois da carga B3, com a `DPL` falhando e a `SOFR` sem carga
- **THEN** a data-base é `2026-09-14`, os contadores mostram 4 curvas `CONSTRUIDA`, 1 `FALHOU` e 2 `AGUARDANDO_CARGA` (`NTNB` e `SOFR`), e as linhas vêm ordenadas por código

### Requirement: Colunas de cada linha
Cada linha SHALL trazer:

| Campo | Fonte |
|---|---|
| `codigo`, `nome`, `unidade`, `situacaoCadastro` (`ATIVO`/`INATIVO`) | `tCurvaMercd` |
| `origem` (provedor, produto, código na fonte) | ligação de menor `cPriorCsumo` em `tCurvaPrvdr` |
| `modeloConstrucao`, `interpolador`, `versaoConfiguracao` | configuração vigente na data-base em `tConfgCurva` |
| `ultimaDataPublicada`, `calculadoPor` | `tCurvaMercd.dBaseReft` e `cUsuarCalc` |
| `situacao`, `atencao`, `atrasada` | regra abaixo |
| `quantidadePontos`, `hashPontos` | pontos gravados em `tDadoCurva` na data-base, com o `hashPontos` da spec `pontos-curva-manual` |
| `carga` (`idCarga`, recebida em, linhas avisadas do código, `republicada`) | engine |
| `ultimaTentativa` (situação, `codigoErro`, mensagem, avisos, `acionadoPor` `CARGA` ou `API`, usuário, instante, duração) | engine |
| `ultimaConstrucao` (`idCarga`, `hashPontos`, instante) | engine |

Campos sem informação SHALL vir nulos, nunca omitidos.

#### Scenario: Curva com falha
- **WHEN** a construção da `DPL` de `2026-09-14` falhou por `INSUMO_INVALIDO` na carga
- **THEN** a linha da `DPL` tem `situacao` = `FALHOU`, `atencao` = `true`, `quantidadePontos` = 0 e `ultimaTentativa` com `INSUMO_INVALIDO`, a mensagem, `acionadoPor` = `CARGA` e o instante

### Requirement: Situação na data-base
A situação SHALL ser decidida nesta ordem, pela primeira regra que se aplica:

| Situação | Regra | `atencao` |
|---|---|---|
| `NAO_E_DIA_UTIL` | a data-base não é dia útil no calendário da configuração vigente da curva, e não há pontos gravados | não |
| `IGNORADA` | curva `INATIVO` ou data-base fora da vigência da curva, e não há pontos gravados | não |
| `EDITADA_MANUALMENTE` | há pontos gravados, e o `hashPontos` gravado é diferente do `hashPontos` da última construção do engine, ou o engine não tem construção registrada | sim |
| `DESATUALIZADA` | há pontos gravados iguais aos da última construção, mas a carga registrada tem `idCarga` diferente do que gerou os pontos (republicação da fonte sem recálculo) | sim |
| `CONSTRUIDA` | há pontos gravados iguais aos da última construção, e da carga registrada | não |
| `FALHOU` | não há pontos gravados, e a última tentativa do engine falhou | sim |
| `CARGA_RECEBIDA` | não há pontos gravados, a carga da origem foi registrada, e não houve tentativa | sim |
| `AGUARDANDO_CARGA` | não há pontos gravados nem carga registrada | só se `atrasada` |
| `SITUACAO_INDISPONIVEL` | o engine não respondeu; `COM_PONTOS` ou `SEM_PONTOS` é informado em `quantidadePontos` | sim |

`atrasada` SHALL ser `true` quando a situação for `AGUARDANDO_CARGA` ou `CARGA_RECEBIDA`, a data-base for hoje, e o horário atual (Brasília) tiver passado do horário esperado do provedor da origem, configurado em `curves.painel.horario-esperado.{provedor}` (ex.: `curves.painel.horario-esperado.B3=20:00`). Para data-base passada, `AGUARDANDO_CARGA` e `CARGA_RECEBIDA` são sempre `atrasada`. Sem horário configurado para o provedor, `atrasada` é `false` na data de hoje.

#### Scenario: Curva editada à mão
- **WHEN** a `PRE` de `2026-09-14` foi construída pelo engine e depois teve um ponto alterado no `services/curves`
- **THEN** a linha da `PRE` tem `situacao` = `EDITADA_MANUALMENTE` e `atencao` = `true`, com o `hashPontos` gravado diferente do de `ultimaConstrucao`

#### Scenario: Republicação sem recálculo
- **WHEN** a B3 republicou o arquivo de `2026-09-14` depois da construção, e ninguém recalculou
- **THEN** as curvas B3 dessa data aparecem como `DESATUALIZADA`, com `carga.republicada` = `true` e os dois `idCarga` (o da carga registrada e o de `ultimaConstrucao`)

#### Scenario: Carga atrasada
- **WHEN** às 20h30 de hoje a SOFR ainda não tem carga, e o horário esperado da `BLOOMBERG` é `19:00`
- **THEN** a linha da `SOFR` tem `situacao` = `AGUARDANDO_CARGA`, `atrasada` = `true` e `atencao` = `true`

#### Scenario: Feriado americano
- **WHEN** a data-base é `2026-11-26` (Thanksgiving, dia útil no Brasil)
- **THEN** a `SOFR` aparece como `NAO_E_DIA_UTIL`, sem `atencao`, e as curvas B3 seguem as demais regras

### Requirement: Dados do engine sem bloquear o painel
O serviço SHALL obter a situação das construções por `GET /api/v1/curvas/situacao?dataBase=` do engine (spec `curve-engine-api` do change `engine-modelos-curva`) e os calendários pela exportação de calendário do engine, com token de serviço e tempo limite de 10 segundos, numa chamada por consulta do painel. O painel MUST NOT falhar por causa do engine: se ele não responder, as linhas SHALL vir com o que o curves tem (cadastro, última data publicada, pontos gravados), `situacao` = `SITUACAO_INDISPONIVEL`, e o aviso geral `ENGINE_INDISPONIVEL`. Se o engine responder com o aviso de que o registro de cargas não pôde ser lido (Blob fora), o painel SHALL repassar esse aviso e tratar as curvas sem informação de carga como `SITUACAO_INDISPONIVEL`.

#### Scenario: Engine fora
- **WHEN** o engine não responde e o gestor abre o painel
- **THEN** a resposta é 200 com todas as curvas, a última data publicada e a quantidade de pontos de cada uma, `situacao` = `SITUACAO_INDISPONIVEL` e o aviso `ENGINE_INDISPONIVEL`
