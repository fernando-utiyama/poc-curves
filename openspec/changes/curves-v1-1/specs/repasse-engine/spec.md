## ADDED Requirements

### Requirement: Rotas de repasse ao engine
O serviço SHALL expor (prefixo `/api/v1`), identificando a curva pelo código e a data-base em `AAAA-MM-DD`:

| Rota | Repassa ao engine |
|---|---|
| `POST /curvas-mercado/{codigo}/{dataBase}/construcao?forcarRecalculo=&fonte=&produto=` | `POST /api/v1/curvas/{codigo}/{dataBase}/construcao` com os parâmetros presentes |
| `POST /curvas-mercado/{codigo}/{dataBase}/interpolada` | `POST /api/v1/curvas/{codigo}/{dataBase}/interpolada` |
| `GET /curvas-mercado/{codigo}/{dataBase}/vertices` | `GET /api/v1/curvas/{codigo}/{dataBase}` |
| `GET /curvas-mercado/{codigo}/{dataBase}/interpolacao?du=&data=` | `GET /api/v1/curvas/{codigo}/{dataBase}/interpolacao` com a query string como veio |

Antes de chamar o engine, o serviço SHALL conferir que a curva existe e tem código (senão 404 `NAO_ENCONTRADO`) e que `fonte` e `produto` vêm juntos ou nenhum e a interpolação traz ao menos um `du` ou `data` (senão 400 `PARAMETRO_INVALIDO`), sem chamar o engine. O `X-Correlation-Id` da requisição SHALL ser repassado ao engine.

#### Scenario: Recálculo pedido pelo front
- **WHEN** o front chama `POST /api/v1/curvas-mercado/PRE/2026-09-14/construcao?forcarRecalculo=true`
- **THEN** a curves chama `POST /api/v1/curvas/PRE/2026-09-14/construcao?forcarRecalculo=true` no engine e devolve a resposta dele

#### Scenario: Origem secundária incompleta
- **WHEN** a chamada traz `fonte=B3` sem `produto`
- **THEN** a resposta é 400 com `PARAMETRO_INVALIDO`, e o engine não é chamado

#### Scenario: Curva inexistente
- **WHEN** a chamada usa o código `XYZ`, que não existe
- **THEN** a resposta é 404 com `NAO_ENCONTRADO`, e o engine não é chamado

### Requirement: Resposta do engine repassada
Nas respostas 2xx, o serviço SHALL devolver o status e o corpo JSON do engine sem alteração. Nas respostas 4xx do engine, o serviço SHALL manter o status, o código e a mensagem do engine no formato de erro que a curves já usa. Se o engine não responder no tempo limite (construção 120 s; regravação 60 s; vértices e interpolação 30 s), estiver fora do ar ou responder 5xx, a resposta SHALL ser 503 `ENGINE_INDISPONIVEL`, sem repetir a chamada.

#### Scenario: Engine sem insumo
- **WHEN** o engine responde 422 `INSUMO_AUSENTE`
- **THEN** a curves responde 422 com o código `INSUMO_AUSENTE` e a mensagem do engine

#### Scenario: Engine fora do ar
- **WHEN** o engine não responde à construção em 120 segundos
- **THEN** a resposta é 503 com `ENGINE_INDISPONIVEL`, e nada é repetido
