## ADDED Requirements

### Requirement: Horário de Brasília
O orquestrador SHALL fixar o fuso padrão da JVM em `America/Sao_Paulo` antes de o Spring subir e recusar a subida se o fuso efetivo for outro. Datas e horários da API e de `tLogTrefa` SHALL usar esse fuso (na API, com `-03:00`), e "hoje" SHALL ser a data de Brasília. O `application.yml` MUST NOT fixar `spring.jackson.time-zone`.

#### Scenario: Servidor em UTC
- **WHEN** o container sobe com o relógio do sistema em UTC às 01h30 de `2026-09-15` (22h30 de `2026-09-14` em Brasília)
- **THEN** uma execução manual sem `dataBase` usa `2026-09-14`

### Requirement: Campos do cadastro gravados e lidos como enviados
O cadastro de tarefa SHALL gravar `action` em `cAcaoOperSist` e `descricao` em `rTrefa`, e a leitura SHALL devolvê-los nos mesmos campos.

#### Scenario: Campos lidos como gravados
- **WHEN** uma tarefa é criada com `action` = `download-carga-dia` e `descricao` = "Baixa o TaxaSwap da B3"
- **THEN** `GET /api/v1/tarefas/{id}` devolve os mesmos valores nos mesmos campos

### Requirement: Chamada de saída restrita a destino cadastrado
As `actions` `http` e `download-carga-dia` MUST NOT aceitar URL livre. Elas SHALL usar o parâmetro `destino` (chave de `orquestrador.http.destinos`, cada uma com `base-url` e `timeout-segundos`) e um caminho sem esquema nem host. `destino` não cadastrado, ausente, ou caminho com esquema, host, `..` ou `//` MUST falhar com erro claro, sem chamada. Toda chamada de saída SHALL levar o `X-Correlation-Id` da execução (UUID gerado por execução, ou o recebido na rota de execução) e MUST NOT levar `Authorization`. O tempo limite SHALL ser o do destino (padrão 120 segundos).

#### Scenario: Destino não cadastrado
- **WHEN** a tarefa tem `destino` = `conector-x`, que não está em `orquestrador.http.destinos`
- **THEN** a execução falha com `DESTINO_NAO_CADASTRADO`, sem nenhuma chamada, e o log da tarefa registra o erro

#### Scenario: Chamada sem Authorization
- **WHEN** o orquestrador chama o destino `conector-b3`
- **THEN** a requisição leva `X-Correlation-Id` e não leva `Authorization`

### Requirement: Action de download da carga, modo manual
A `action` `download-carga-dia` SHALL ler os parâmetros da tarefa:

| Parâmetro | Uso |
|---|---|
| `fonte` | `B3`, `ANBIMA` ou `BLOOMBERG`, só para log e resultado |
| `destino` | chave de `orquestrador.http.destinos` |
| `caminhoDownload` | caminho com `{dataBase}` e, na Bloomberg, `{tickers}` |
| `caminhoReprocessamento` | opcional; caminho usado para data-base passada |
| `tickers` | só na Bloomberg; lista separada por vírgula |
| `inicioHorario`, `limiteHorario`, `calendarios` | aceitos e guardados, sem efeito na v0 (usados pelo agendamento da v1) |

Com a data-base de hoje, a `action` SHALL chamar o `caminhoDownload`; com data passada, o `caminhoReprocessamento` (se houver; senão, o `caminhoDownload`). Data futura MUST ser recusada (400 `PARAMETRO_INVALIDO`). `{dataBase}` SHALL ser trocado pela data em `AAAA-MM-DD`, e `{tickers}` pelo parâmetro `tickers` com codificação de URL; caminho com `{tickers}` e tarefa sem o parâmetro MUST falhar com erro claro, sem chamada. O método SHALL ser `GET`. A resposta SHALL ser classificada:

| Resposta | Resultado |
|---|---|
| 200 com `dataBase` igual à pedida | `SUCESSO`, com o `idCarga` |
| 200 com outra `dataBase`, 502, 503, tempo esgotado ou erro de rede | `NAO_RECEBIDA` |
| 501 (provedor ainda não implementado no processor) | `NAO_IMPLEMENTADA` |
| qualquer outra (400, 404, 422, 5xx diferente dos acima) | `ERRO` |

Cada execução é uma tentativa só: a v0 não repete, não consulta janela nem calendário e não grava alerta.

#### Scenario: Download do dia com sucesso
- **WHEN** o operador executa a tarefa B3 sem `dataBase` em `2026-09-14`, e o destino responde 200 com `dataBase` = `2026-09-14` e `idCarga` = `B3-TS-20260914-1a2b3c4d5e6f`
- **THEN** o orquestrador chamou `{base-url do conector-b3}/api/b3/taxa-swap/download?date=2026-09-14`, e o resultado é `SUCESSO` com o `idCarga`

#### Scenario: Forçar uma data antiga
- **WHEN** o operador executa a tarefa B3 com `dataBase` = `2026-09-10`
- **THEN** o orquestrador chama o `caminhoReprocessamento` com `2026-09-10`

#### Scenario: Bloomberg com tickers
- **WHEN** o operador executa a tarefa Bloomberg com o parâmetro `tickers` = `S0490Z 1M BLC2 Curncy,S0490Z 3M BLC2 Curncy`
- **THEN** a chamada leva a data e os tickers codificados no lugar de `{tickers}`

#### Scenario: Arquivo do dia ainda não saiu
- **WHEN** o destino responde 503
- **THEN** o resultado é `NAO_RECEBIDA`, sem nova tentativa automática

### Requirement: Execução manual com data-base
`POST /api/v1/agendador/tarefas/{id}/executar` SHALL aceitar o parâmetro opcional `dataBase` (`AAAA-MM-DD`; padrão: hoje em Brasília) e o cabeçalho opcional `X-Usuario` (ausente: usuário nulo). Antes de executar, o orquestrador SHALL reivindicar a tarefa no banco: numa transação curta, um `UPDATE` condicional de `cSit` para `EXECUTANDO` só se a situação atual não for `EXECUTANDO`, `DESABILITADA` nem `REMOVIDA` (comparada depois de `trim`), e o log de início (código `102`) com a situação de origem, a data-base, o usuário e a instância em JSON. Zero linhas atualizadas com a tarefa `EXECUTANDO` SHALL responder 409 sem esperar; `DESABILITADA` ou `REMOVIDA`, 400. A reivindicação vale entre instâncias, porque está no banco. A `action` roda fora da transação; ao fim, a tarefa SHALL voltar à situação de origem, e o log SHALL ter o resultado: código `200` para `SUCESSO` e `500` para os demais, com texto JSON `{ "resultado", "fonte", "dataBase", "idCarga", "statusHttp", "detalhe", "usuario", "correlationId" }`. A resposta da rota SHALL ser 200 com esse mesmo JSON, inclusive quando o resultado não é `SUCESSO`: a execução foi feita e o resultado é informação para o operador.

#### Scenario: Duas execuções ao mesmo tempo
- **WHEN** dois operadores pedem `/executar` da tarefa ANBIMA ao mesmo tempo, em instâncias diferentes
- **THEN** uma executa e a outra recebe 409, e só uma chamada sai para o destino

#### Scenario: Execução volta à situação de origem
- **WHEN** a tarefa B3 `PRONTA` é executada manualmente com resultado `NAO_RECEBIDA`
- **THEN** ao fim a tarefa está `PRONTA`, o log tem o código `102` e o `500` com o resultado, e a resposta é 200 com `resultado` = `NAO_RECEBIDA`

#### Scenario: Execução sem cabeçalho de usuário
- **WHEN** `/executar` chega sem `X-Usuario`
- **THEN** a tarefa executa normalmente, com o usuário nulo no log
