## Purpose

Define o que o orquestrador faz no processo das curvas: disparar, em cada function de fonte, o download da carga do dia (B3 `TaxaSwap`, ANBIMA `ms`, Bloomberg SOFR), tentar de novo quando o arquivo do dia ainda não saiu, alertar no dashboard o que não chegou e permitir o disparo manual de uma data. São tarefas cadastradas no motor genérico (spec `agendamento-tarefas`), todas com a mesma `action`; o orquestrador não calcula nem interpreta curvas, e não chama o engine para construir: depois do download, o mesmo fluxo da B3 segue sozinho (a function grava o arquivo no Blob e avisa, o processor grava o bruto e chama o webhook do engine, que constrói as curvas da carga e, em cadeia, as derivadas).

## ADDED Requirements

### Requirement: `action` de download da carga do dia
`CargaFonteTaskActionAdapter` SHALL obter a carga do dia de uma fonte pela function dela, com novas tentativas até um horário limite. Ele atende duas `actions`, que dizem como a fonte entrega o dado: `carga-download-site` (arquivo público baixado do site: B3 e ANBIMA) e `carga-data-license` (pedido à API do Bloomberg Data License, com `tickers` obrigatório). O comportamento é o mesmo nas duas; cada fonte é uma tarefa cadastrada com os parâmetros:

| Parâmetro | Uso |
|---|---|
| `fonte` | rótulo da fonte nos logs e no alerta (`B3`, `ANBIMA`, `BLOOMBERG`) |
| `destino` | a function da fonte (chave de `orquestrador.http.destinos`, ex.: `conector-b3`, `conector-anbima`, `conector-bloomberg`; lista de destinos da spec `agendamento-tarefas`) |
| `caminhoDownload` | caminho com `{dataBase}` (ex.: `/api/b3/taxa-swap/download?date={dataBase}`) e, na Bloomberg, também `{tickers}` |
| `caminhoReprocessamento` | opcional; caminho com `{dataBase}` (e `{tickers}`) usado na execução manual de data passada |
| `tickers` | só na Bloomberg: lista separada por vírgula dos tickers a buscar (ex.: `S0490Z 1M BLC2 Curncy,S0490Z 3M BLC2 Curncy`), enviada à function junto com a data no lugar de `{tickers}`, com codificação de URL; obrigatório quando um caminho tem `{tickers}`, e MUST NOT ser exigido nas outras fontes |
| `inicioHorario`, `limiteHorario` | janela de tentativas (`HH:mm`, Brasília) |
| `defasagemDiasUteis` | opcional, 0 a 10 (padrão 0): a data-base padrão é hoje recuado essa quantidade de dias úteis nos `calendarios` da tarefa (como na change `orquestrador-v0-disparo-manual`) |

A tarefa SHALL ter `regraIntervalo` com o intervalo entre tentativas (ex.: `PT10M`, ocorrências alinhadas à meia-noite de Brasília); o cron não é usado porque uma janela "a cada 10 minutos" não cabe nos 15 caracteres da coluna. A function de cada fonte MUST responder no contrato do download B3 (change `conector-b3-webhook-ingest`): 200 com a `dataBase` do arquivo e o `idCarga`.

O orquestrador MUST NOT disparar o engine: o 200 da function encerra o trabalho dele naquele dia, e a construção das curvas segue pelo fluxo da function e do processor.

Antes de reivindicar a ocorrência (sem gravar log), a `action` SHALL encerrar se: a ocorrência é anterior ao `inicioHorario`; já existe log de sucesso da tarefa com a data-base de hoje; ou já existe o alerta `CARGA_NAO_RECEBIDA` da tarefa com a data-base de hoje. Os logs de tentativa, sucesso e alerta SHALL trazer a data-base em JSON, para que um reprocessamento manual de outra data não conte como o sucesso de hoje. Nos demais casos, cada ocorrência é uma tentativa:

- 200 com `dataBase` igual à pedida: sucesso, log com o `idCarga`;
- 200 com outra `dataBase` (arquivo de dia anterior), 502, 503, tempo esgotado ou erro de rede: "ainda não recebida", log da tentativa; a próxima ocorrência tenta de novo;
- 400 ou 422: log de erro; a próxima ocorrência tenta de novo (repetir na mesma execução não resolve);
- na primeira ocorrência igual ou posterior ao `limiteHorario`, a tentativa é feita normalmente e, se o arquivo do dia ainda não veio, SHALL gravar também o alerta `CARGA_NAO_RECEBIDA` (fonte, data-base, quantidade de tentativas de hoje, última resposta), uma vez por dia; as ocorrências seguintes do dia encerram antes da reivindicação.

Cada fonte tem a sua tarefa, então as tentativas, o sucesso e o alerta de uma fonte não interferem nos da outra.

#### Scenario: Fora da janela
- **WHEN** a ocorrência das 10h10 da tarefa B3 dispara, com `inicioHorario` = `18:00`
- **THEN** nada é chamado, e nenhum log é gravado

#### Scenario: B3 ainda não publicou
- **WHEN** a ocorrência das 18h de `2026-09-14` da tarefa B3 recebe 200 com `dataBase` = `2026-09-11`
- **THEN** a tentativa é registrada, e a ocorrência das 18h10 tenta de novo

#### Scenario: Arquivo do dia recebido
- **WHEN** a ocorrência das 18h40 da tarefa B3 recebe 200 com `dataBase` = `2026-09-14`
- **THEN** o log registra sucesso com o `idCarga`, as ocorrências seguintes do dia dessa tarefa encerram sem chamar nada, e o orquestrador não chama o engine

#### Scenario: Bloomberg com os tickers
- **WHEN** a ocorrência das 18h20 da tarefa Bloomberg dispara em dia útil no Brasil e nos Estados Unidos, com `tickers` cadastrado
- **THEN** a chamada à function da Bloomberg leva a data-base e os tickers, e o 200 com a `dataBase` do dia é o sucesso da tarefa

#### Scenario: Tickers ausentes na Bloomberg
- **WHEN** o caminho da tarefa tem `{tickers}` e o parâmetro `tickers` não está cadastrado
- **THEN** a execução falha com erro claro sobre o parâmetro, sem nenhuma chamada

#### Scenario: Horário limite sem sucesso
- **WHEN** chega a ocorrência do `limiteHorario` da tarefa ANBIMA sem sucesso no dia, e essa última tentativa também não traz o arquivo do dia
- **THEN** o alerta `CARGA_NAO_RECEBIDA` com fonte `ANBIMA` é gravado uma vez, e as ocorrências seguintes do dia dessa tarefa encerram sem chamar nada

#### Scenario: Arquivo sai no limite
- **WHEN** a tentativa da ocorrência do `limiteHorario` da tarefa B3 recebe o arquivo do dia
- **THEN** o log registra sucesso, e nenhum alerta é gravado

#### Scenario: Fontes independentes
- **WHEN** o arquivo B3 do dia já chegou e o ANBIMA ainda não
- **THEN** a tarefa B3 não chama mais nada no dia, e a tarefa ANBIMA continua tentando até o seu limite

#### Scenario: Uma instância cai na janela
- **WHEN** a instância A cai às 18h05
- **THEN** a ocorrência das 18h10 roda na instância B

### Requirement: Alerta no dashboard
`CARGA_NAO_RECEBIDA` SHALL ser gravado como alerta da própria tarefa (requisito "Alertas consultáveis" da spec `agendamento-tarefas`) e SHALL aparecer em `GET /api/v1/alertas`, que o dashboard do front consome. O orquestrador SHALL também incrementar um contador Micrometer do alerta, com a fonte como rótulo. Nenhum alerta é enviado por e-mail ou Teams.

#### Scenario: Carga não recebida visível no front
- **WHEN** a tarefa ANBIMA grava `CARGA_NAO_RECEBIDA` no seu limite
- **THEN** o alerta aparece na consulta de alertas do dia, com a fonte `ANBIMA`, a data-base e as tentativas

### Requirement: Data-base e dia útil
A data-base de uma execução agendada SHALL ser a data-base padrão da tarefa: a data de hoje no fuso `America/Sao_Paulo`, independente do fuso do servidor, recuada `defasagemDiasUteis` dias úteis (padrão 0) nos `calendarios` da tarefa. Nas regras de encerramento, "data-base de hoje" é essa data-base padrão. O dia útil SHALL ser decidido pelo parâmetro `calendarios` de cada tarefa (requisito "Calendário de dias úteis por tarefa" da spec `agendamento-tarefas`): `Brazil/Settlement` nos downloads B3 e ANBIMA; `Brazil/Settlement,UnitedStates/FederalReserve` no download Bloomberg (a SOFR só tem dado novo em dia útil americano, e a curva só é construída em dia útil brasileiro).

#### Scenario: Servidor em UTC perto da meia-noite
- **WHEN** a ocorrência é às 21h30 de Brasília e o servidor está em UTC
- **THEN** a execução acontece às 21h30 de Brasília, com a data-base do dia em Brasília

#### Scenario: Feriado americano
- **WHEN** chega `2026-11-26` (dia útil no Brasil, Dia de Ação de Graças nos Estados Unidos)
- **THEN** os downloads B3 e ANBIMA rodam, e o download Bloomberg não

### Requirement: Cadastro das tarefas de curva
As tarefas SHALL ser cadastradas pela API de tarefas (`POST /api/v1/tarefas`), com `regraIntervalo` (todas tentam de 10 em 10 minutos dentro da sua janela) e os parâmetros da `action`:

| Tarefa | `action` | Parâmetros |
|---|---|---|
| Carga B3 (site) | `carga-download-site` | `fonte`=`B3`, `destino` da function B3, `caminhoDownload`=`/api/b3/taxa-swap/download?date={dataBase}`, `caminhoReprocessamento`=`/api/b3/taxa-swap/reprocessamento?dataBase={dataBase}`, janela |
| Carga ANBIMA (site) | `carga-download-site` | `fonte`=`ANBIMA`, `destino` da function ANBIMA, caminhos da rota dela (change própria), janela |
| Carga Bloomberg (Data License) | `carga-data-license` | `fonte`=`BLOOMBERG`, `destino` da function Bloomberg, caminhos da rota dela (change própria) com `{dataBase}` e `{tickers}`, `tickers`, janela |

Os cadastros são feitos pela tela; a sugestão de cada um está em `cadastros-sugeridos.txt` desta change. A tarefa de uma fonte só é cadastrada quando a rota da function dela existir. Depois de cadastrada (`PRONTA`), a tarefa SHALL ser agendada (`POST /api/v1/agendador/tarefas/{id}/agendar`) para disparar sozinha. Não há horário embutido no orquestrador: sem cadastro, a tarefa não existe. Mudar horário, intervalo, limite ou tickers SHALL ser um `PATCH`, sem redeploy; agendar e desagendar é o liga/desliga do disparo automático.

#### Scenario: Mudar o horário sem redeploy
- **WHEN** um operador faz `PATCH` no `limiteHorario` da tarefa B3
- **THEN** a próxima ocorrência já usa o novo limite, sem reiniciar o orquestrador

#### Scenario: Incluir um ticker sem redeploy
- **WHEN** um operador faz `PATCH` em `tickers` da tarefa Bloomberg
- **THEN** a próxima ocorrência envia a nova lista à function

### Requirement: Execução manual
A execução manual SHALL usar a rota do motor (`POST /api/v1/agendador/tarefas/{id}/executar`), aceitando uma `dataBase` opcional, informada pelo front (padrão: a data-base padrão da tarefa), inclusive passada ou não útil, e SHALL seguir as regras da `action`, registrando o usuário. A execução manual SHALL aceitar também `incluirDownload` (padrão `false`): com ele, uma `dataBase` anterior à data-base padrão chama o `caminhoDownload` daquela data, buscando de novo na fonte. Sem ele, uma `dataBase` anterior à data-base padrão SHALL chamar o `caminhoReprocessamento` da tarefa (se houver; senão, o `caminhoDownload`) em vez do download, e a execução manual é uma tentativa só, sem `inicioHorario` nem `limiteHorario` e sem alerta. Execução manual nunca grava alerta.

#### Scenario: Forçar uma data antiga
- **WHEN** o operador executa a tarefa B3 com `dataBase` = `2026-09-10`
- **THEN** o orquestrador chama o `caminhoReprocessamento` da function B3 para `2026-09-10`, e o log registra o usuário e o resultado

### Requirement: Chamadas à function, correlação e logs
Toda chamada às functions e ao engine (este só para o calendário) SHALL respeitar o tempo limite do destino (`orquestrador.http.destinos.{destino}.timeout-segundos`; padrão 120 nas functions e 30 no engine) e MUST NOT levar `Authorization` (as functions e o engine não exigem autenticação). Cada execução SHALL ter um `correlationId` (UUID), enviado em `X-Correlation-Id` em toda chamada dela e registrado em todo log dela. Nenhum log MUST conter segredo.

#### Scenario: Chamada sem Authorization
- **WHEN** o orquestrador chama uma function ou o engine
- **THEN** a requisição leva `X-Correlation-Id` e não leva `Authorization`
