## Purpose

Na `services/curves`, repassar ao engine as ações e consultas que o gestor faz sobre uma curva de mercado numa data-base (construir, recalcular, regravar a curva interpolada, consultar os vértices e os pontos interpolados e interpolar prazos), para o front falar só com a curves.

## ADDED Requirements

### Requirement: Rotas de ação da curva numa data-base
O serviço SHALL expor (prefixo `/api/v1`), identificando a curva pelo código (`tCurvaMercd.cTickerIdtfdUnic`) e a data-base em `AAAA-MM-DD`:

| Rota | Repassa ao engine |
|---|---|
| `POST /curvas-mercado/{codigo}/{dataBase}/construcao?forcarRecalculo=&fonte=&produto=` | `POST /api/v1/curvas/{codigo}/{dataBase}/construcao` com os mesmos parâmetros |
| `POST /curvas-mercado/{codigo}/{dataBase}/interpolada` | `POST /api/v1/curvas/{codigo}/{dataBase}/interpolada` |
| `GET /curvas-mercado/{codigo}/{dataBase}/vertices` | `GET /api/v1/curvas/{codigo}/{dataBase}` (JSON) |
| `GET /curvas-mercado/{codigo}/{dataBase}/pontos?de=&ate=` | `GET /api/v1/curvas/{codigo}/{dataBase}/pontos` com os mesmos parâmetros (spec `pontos-interpolados-engine`) |
| `GET /curvas-mercado/{codigo}/{dataBase}/interpolacao?du=&data=` | `GET /api/v1/curvas/{codigo}/{dataBase}/interpolacao` com os mesmos parâmetros, repetidos como vieram |

Antes de chamar o engine, o serviço SHALL conferir que a curva existe e tem código (senão 404 `NAO_ENCONTRADO`) e validar os parâmetros: data inválida, `forcarRecalculo` diferente de `true`/`false`, só um entre `fonte` e `produto`, ou interpolação sem nenhum `du` nem `data` respondem 400 `PARAMETRO_INVALIDO`, sem chamar o engine. A autenticação, os papéis, o formato de erro, o `X-Correlation-Id` e o horário seguem a spec `cadastro-curva-mercado`.

#### Scenario: Recálculo pedido pelo front
- **WHEN** o gestor chama `POST /api/v1/curvas-mercado/PRE/2026-09-14/construcao?forcarRecalculo=true`
- **THEN** a curves chama `POST /api/v1/curvas/PRE/2026-09-14/construcao?forcarRecalculo=true` no engine e devolve a resposta dele

#### Scenario: Origem secundária incompleta
- **WHEN** a chamada traz `fonte=B3` sem `produto`
- **THEN** a resposta é 400 com `PARAMETRO_INVALIDO`, e o engine não é chamado

#### Scenario: Pontos de uma semana
- **WHEN** o front chama `GET /api/v1/curvas-mercado/PRE/2026-09-14/pontos?de=2026-09-15&ate=2026-09-21`
- **THEN** a curves chama a mesma consulta no engine e devolve os 7 pontos

#### Scenario: Curva inexistente
- **WHEN** a chamada usa o código `XYZ`, que não existe
- **THEN** a resposta é 404 com `NAO_ENCONTRADO`, e o engine não é chamado

### Requirement: Resposta do engine repassada sem alteração
Nas respostas 2xx, o serviço SHALL devolver ao front o status HTTP e o corpo JSON que o engine respondeu, sem reescrever campos, para o front mostrar a situação (`CONSTRUIDA`, `RECONSTRUIDA`, `EXISTENTE`), os avisos e os dados do próprio engine. Nas respostas 4xx do engine (por exemplo, 422 `INSUMO_AUSENTE`, 404 `CURVA_NAO_CONSTRUIDA`, 409 `CONSTRUCAO_EM_ANDAMENTO`), o serviço SHALL manter o status, o código do erro, a mensagem e os `detalhes` do engine, mas no formato de erro da própria curves (spec `cadastro-curva-mercado`), para o front ler um formato só. O `X-Correlation-Id` da requisição SHALL ser repassado ao engine.

Se o engine não responder no tempo limite da ação (construção: 120 segundos; regravação da interpolada: 60 segundos; consulta de vértices, de pontos e interpolação: 30 segundos), ou estiver fora do ar, ou responder 5xx, a resposta SHALL ser 503 com o código novo `ENGINE_INDISPONIVEL`, no formato de erro do serviço, e a ação MUST NOT ser repetida pelo serviço.

#### Scenario: Engine sem insumo
- **WHEN** o engine responde 422 `INSUMO_AUSENTE` para a `DPL` numa data sem dado bruto
- **THEN** a curves responde 422 com o código `INSUMO_AUSENTE` e a mensagem do engine no formato de erro da curves

#### Scenario: Engine fora do ar
- **WHEN** o engine não responde à construção em 120 segundos
- **THEN** a resposta é 503 com `ENGINE_INDISPONIVEL`, e nada é repetido

### Requirement: Usuário da ação
Sem autenticação na v0 e na v1, o serviço SHALL repassar ao engine o cabeçalho `X-Usuario` recebido, quando vier, para o engine registrá-lo na auditoria da construção (`acionadoPor` = `API`); sem ele, o usuário fica nulo. O serviço MUST NOT enviar `Authorization` ao engine.

#### Scenario: Usuário na auditoria do engine
- **WHEN** a curves recebe o recálculo da `PRE` de `2026-09-14` com `X-Usuario` = `maria`
- **THEN** o engine recebe `X-Usuario` = `maria` e nenhum `Authorization`, e o `CURVA_GRAVADA` traz `usuario` = `maria` e `acionadoPor` = `API`; sem o cabeçalho, `usuario` é nulo
