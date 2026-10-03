## ADDED Requirements

### Requirement: Horário de Brasília
O orquestrador SHALL fixar o fuso padrão da JVM em `America/Sao_Paulo` antes de o Spring subir e recusar a subida se o fuso efetivo for outro. Datas e horários da API e de `tLogTrefa` SHALL usar esse fuso (na API, com `-03:00`), e "hoje" SHALL ser a data de Brasília. O `application.yml` MUST NOT fixar `spring.jackson.time-zone`.

#### Scenario: Servidor em UTC
- **WHEN** o container sobe com o relógio do sistema em UTC às 01h30 de `2026-09-15` (22h30 de `2026-09-14` em Brasília)
- **THEN** uma execução manual sem `dataBase` usa `2026-09-14`

### Requirement: Campos do cadastro gravados e lidos como enviados
O cadastro de tarefa SHALL gravar `action` em `cAcaoOperSist` e `descricao` em `rTrefa`, e a leitura SHALL devolvê-los nos mesmos campos.

#### Scenario: Campos lidos como gravados
- **WHEN** uma tarefa é criada com `action` = `carga-download-site` e `descricao` = "Baixa o TaxaSwap da B3"
- **THEN** `GET /api/v1/tarefas/{id}` devolve os mesmos valores nos mesmos campos

### Requirement: Chamada de saída restrita a destino cadastrado
As `actions` `http`, `carga-download-site` e `carga-data-license` MUST NOT aceitar URL livre. Elas SHALL usar o parâmetro `destino` (chave de `orquestrador.http.destinos`, cada uma com `base-url` e `timeout-segundos`) e um caminho sem esquema nem host. `destino` não cadastrado, ausente, ou caminho com esquema, host, `..` ou `//` MUST falhar com erro claro, sem chamada. Toda chamada de saída SHALL levar o `X-Correlation-Id` da execução (UUID gerado por execução, ou o recebido na rota de execução) e MUST NOT levar `Authorization`. O tempo limite SHALL ser o do destino (padrão 120 segundos).

#### Scenario: Destino não cadastrado
- **WHEN** a tarefa tem `destino` = `conector-x`, que não está em `orquestrador.http.destinos`
- **THEN** a execução falha com `DESTINO_NAO_CADASTRADO`, sem nenhuma chamada, e o log da tarefa registra o erro

#### Scenario: Chamada sem Authorization
- **WHEN** o orquestrador chama o destino `conector-b3`
- **THEN** a requisição leva `X-Correlation-Id` e não leva `Authorization`

### Requirement: Action de download da carga, modo manual
As `actions` de carga SHALL dizer como a fonte entrega o dado: `carga-download-site` (arquivo público baixado do site: B3 e ANBIMA) e `carga-data-license` (pedido à API do Bloomberg Data License: SOFR). As duas têm o mesmo comportamento e SHALL ler os parâmetros da tarefa:

| Parâmetro | Uso |
|---|---|
| `fonte` | `B3`, `ANBIMA` ou `BLOOMBERG`, só para log e resultado |
| `destino` | chave de `orquestrador.http.destinos` |
| `caminhoDownload` | caminho com `{dataBase}` e, na Bloomberg, `{tickers}` |
| `caminhoReprocessamento` | opcional; caminho usado para data-base passada |
| `tickers` | obrigatório em `carga-data-license` (lista separada por vírgula); ausente ou vazio MUST falhar com erro claro, sem chamada |
| `defasagemDiasUteis` | opcional; inteiro de 0 a 10 (padrão 0): quantos dias úteis a data-base padrão fica antes de hoje |
| `calendarios` | opcional (padrão `Brazil/Settlement`); calendários em que se contam os dias úteis da defasagem (na v1, também os dias em que a tarefa roda) |
| `inicioHorario`, `limiteHorario` | aceitos e guardados, sem efeito na v0 (usados pelo agendamento da v1) |

A **data-base padrão** da tarefa SHALL ser hoje em Brasília recuado `defasagemDiasUteis` dias úteis, contando só os dias úteis em **todos** os `calendarios` da tarefa (com defasagem 0, é hoje, mesmo que não seja dia útil). Com a data-base igual ou posterior à padrão, a `action` SHALL chamar o `caminhoDownload`. Anterior à padrão, é um reprocessamento: com `incluirDownload` = `false` (padrão), a `action` SHALL chamar o `caminhoReprocessamento` (o destino relê o original já guardado; sem `caminhoReprocessamento`, o `caminhoDownload`); com `incluirDownload` = `true`, SHALL chamar o `caminhoDownload` com aquela data, para buscar o arquivo de novo na fonte. Data futura MUST ser recusada (400 `PARAMETRO_INVALIDO`), assim como `defasagemDiasUteis` fora de 0 a 10 ou `calendarios` desconhecido. `{dataBase}` SHALL ser trocado pela data em `AAAA-MM-DD`, e `{tickers}` pelo parâmetro `tickers` com codificação de URL; caminho com `{tickers}` e tarefa sem o parâmetro MUST falhar com erro claro, sem chamada. O método SHALL ser `GET`. A resposta SHALL ser classificada:

| Resposta | Resultado |
|---|---|
| 200 com `dataBase` igual à pedida | `SUCESSO`, com o `idCarga` |
| 200 com outra `dataBase`, 502, 503, tempo esgotado ou erro de rede | `NAO_RECEBIDA` |
| 501 (provedor ainda não implementado no processor) | `NAO_IMPLEMENTADA` |
| qualquer outra (400, 404, 422, 5xx diferente dos acima) | `ERRO` |

Cada execução é uma tentativa só: a v0 não repete, não consulta janela, não deixa de executar por causa do calendário (ele só conta a defasagem) e não grava alerta.

#### Scenario: Download do dia com sucesso
- **WHEN** o operador executa a tarefa B3 sem `dataBase` em `2026-09-14`, e o destino responde 200 com `dataBase` = `2026-09-14` e `idCarga` = `B3-TS-20260914-1a2b3c4d5e6f`
- **THEN** o orquestrador chamou `{base-url do conector-b3}/api/v1/cargas/b3/download?dataBase=2026-09-14`, e o resultado é `SUCESSO` com o `idCarga`

#### Scenario: Defasagem de um dia útil
- **WHEN** a tarefa Bloomberg tem `defasagemDiasUteis` = 1 e `calendarios` = `Brazil/Settlement,UnitedStates/FederalReserve`, e o operador executa sem data na segunda-feira `2026-09-14`
- **THEN** a data-base é a sexta-feira `2026-09-11`, e o orquestrador chama o `caminhoDownload` com `2026-09-11`

#### Scenario: Defasagem pulando feriado
- **WHEN** a tarefa B3 tem `defasagemDiasUteis` = 1 e é executada sem data em `2026-11-03` (terça), com `2026-11-02` feriado nacional
- **THEN** a data-base é `2026-10-30` (sexta)

#### Scenario: Forçar uma data antiga
- **WHEN** o operador executa a tarefa B3 com `dataBase` = `2026-09-10`
- **THEN** o orquestrador chama o `caminhoReprocessamento` com `2026-09-10`

#### Scenario: Reprocessamento com download
- **WHEN** o operador executa a tarefa ANBIMA com `dataBase` = `2026-09-10` e `incluirDownload` = `true`
- **THEN** o orquestrador chama o `caminhoDownload` com `2026-09-10`, e o destino busca o arquivo dessa data na fonte

#### Scenario: Bloomberg com tickers
- **WHEN** o operador executa a tarefa Bloomberg com o parâmetro `tickers` = `S0490Z 1M BLC2 Curncy,S0490Z 3M BLC2 Curncy`
- **THEN** a chamada leva a data e os tickers codificados no lugar de `{tickers}`

#### Scenario: Arquivo do dia ainda não saiu
- **WHEN** o destino responde 503
- **THEN** o resultado é `NAO_RECEBIDA`, sem nova tentativa automática

### Requirement: Execução manual com data-base
`POST /api/v1/agendador/tarefas/{id}/executar` SHALL aceitar os parâmetros opcionais `dataBase` (`AAAA-MM-DD`; sem ela, a data-base padrão da tarefa) e `incluirDownload` (`true`/`false`, padrão `false`; só tem efeito com data anterior à padrão) e o cabeçalho opcional `X-Usuario` (ausente: usuário nulo). Antes de executar, o orquestrador SHALL reivindicar a tarefa no banco: numa transação curta, um `UPDATE` condicional de `cSit` para `EXECUTANDO` só se a situação atual não for `EXECUTANDO`, `DESABILITADA` nem `REMOVIDA` (comparada depois de `trim`), e o log de início (código `102`) com a situação de origem, a data-base, o usuário e a instância em JSON. Zero linhas atualizadas com a tarefa `EXECUTANDO` SHALL responder 409 sem esperar; `DESABILITADA` ou `REMOVIDA`, 400. A reivindicação vale entre instâncias, porque está no banco. A `action` roda fora da transação; ao fim, a tarefa SHALL voltar à situação de origem, e o log SHALL ter o resultado: código `200` para `SUCESSO` e `500` para os demais, com texto JSON `{ "resultado", "fonte", "dataBase", "incluirDownload", "idCarga", "statusHttp", "detalhe", "usuario", "correlationId" }`. A resposta da rota SHALL ser 200 com esse mesmo JSON, inclusive quando o resultado não é `SUCESSO`: a execução foi feita e o resultado é informação para o operador.

#### Scenario: Duas execuções ao mesmo tempo
- **WHEN** dois operadores pedem `/executar` da tarefa ANBIMA ao mesmo tempo, em instâncias diferentes
- **THEN** uma executa e a outra recebe 409, e só uma chamada sai para o destino

#### Scenario: Execução volta à situação de origem
- **WHEN** a tarefa B3 `PRONTA` é executada manualmente com resultado `NAO_RECEBIDA`
- **THEN** ao fim a tarefa está `PRONTA`, o log tem o código `102` e o `500` com o resultado, e a resposta é 200 com `resultado` = `NAO_RECEBIDA`

#### Scenario: Execução sem cabeçalho de usuário
- **WHEN** `/executar` chega sem `X-Usuario`
- **THEN** a tarefa executa normalmente, com o usuário nulo no log

### Requirement: Calendários nativos no orquestrador
O orquestrador SHALL ter os mesmos calendários nativos do engine (`Brazil`/`Settlement` e `UnitedStates`/`FederalReserve`), copiados da seção 4 do guia da change `engine-construcao-curvas` sem mudar regra, sem depender do engine no ar. Na v0 não há sincronização de feriados decretados com o engine (é da v1).

#### Scenario: Mesmos feriados do engine
- **WHEN** o orquestrador consulta os feriados de 2026
- **THEN** `Brazil`/`Settlement` tem a Páscoa em `2026-04-05` (Carnaval `02-16`/`02-17`, Sexta-feira Santa `04-03`, Corpus Christi `06-04`), e `UnitedStates`/`FederalReserve` tem `01-01`, `01-19`, `02-16`, `05-25`, `06-19`, `09-07`, `10-12`, `11-11`, `11-26` e `12-25`, as mesmas datas do engine

### Requirement: Data-base escolhida no front
O front SHALL permitir ao operador executar cada tarefa de download informando a data-base (campo opcional, `dd/mm/aaaa`); vazio, vale a data-base padrão da tarefa, e a tela SHALL dizer isso ("Vazio: data-base padrão da tarefa"). Com uma data informada, a tela SHALL oferecer a opção "Baixar de novo da fonte" (desmarcada), que envia `incluirDownload` = `true`; sem marcá-la, uma data passada reprocessa o arquivo já guardado. O bff SHALL expor a execução autenticada, só para o perfil de operação, e repassar ao orquestrador `POST /api/v1/agendador/tarefas/{id}/executar` com a `dataBase` (em `AAAA-MM-DD`, ou sem ela) e o `incluirDownload`, `X-Usuario` = usuário autenticado e `X-Correlation-Id`, sem o token. A tela SHALL mostrar o resultado devolvido: situação ("Sucesso", "Arquivo ainda não recebido", "Fonte ainda não implementada", "Erro"), a data-base usada (`dd/mm/aaaa`), o identificador da carga e o detalhe do erro.

#### Scenario: Execução sem data pelo front
- **WHEN** o operador clica em executar na tarefa B3 sem informar data
- **THEN** o bff chama o orquestrador sem `dataBase`, e a tela mostra a data-base padrão usada e o resultado

#### Scenario: Execução com data pelo front
- **WHEN** o operador informa `10/09/2026` e executa a tarefa ANBIMA
- **THEN** o bff chama o orquestrador com `dataBase` = `2026-09-10` e `incluirDownload` = `false`, e o orquestrador chama o reprocessamento
