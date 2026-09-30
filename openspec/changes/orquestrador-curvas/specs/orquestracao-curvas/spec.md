## Purpose

Define o que o orquestrador faz no processo das curvas: disparar no conector o download da carga do dia de cada fonte (B3 `TaxaSwap`, ANBIMA `ms`, Bloomberg SOFR) e a construção automática da data no engine, tentar de novo quando o arquivo do dia ainda não saiu, alertar no dashboard o que não aconteceu e permitir o disparo manual de uma data. São tarefas cadastradas no motor genérico (spec `agendamento-tarefas`), cada uma com a sua `action`; o orquestrador não calcula nem interpreta curvas.

## ADDED Requirements

### Requirement: `action` de download da carga do dia
`DownloadCargaTaskActionAdapter` (`action` = `download-carga-dia`) SHALL baixar a carga do dia de uma fonte pelo conector, com novas tentativas até um horário limite. Uma mesma `action` atende todas as fontes; cada fonte é uma tarefa cadastrada com os parâmetros:

| Parâmetro | Uso |
|---|---|
| `fonte` | rótulo da fonte nos logs e no alerta (ex.: `B3`, `ANBIMA`, `BLOOMBERG`) |
| `destino` | `conector` (lista de destinos da spec `agendamento-tarefas`) |
| `caminhoDownload` | caminho com `{dataBase}` (ex.: `/api/b3/taxa-swap/download?date={dataBase}`) |
| `caminhoReprocessamento` | opcional; caminho com `{dataBase}` usado na execução manual de data passada |
| `inicioHorario`, `limiteHorario` | janela de tentativas (`HH:mm`, Brasília) |

A tarefa SHALL ter `regraIntervalo` com o intervalo entre tentativas (ex.: `PT10M`, ocorrências alinhadas à meia-noite de Brasília); o cron não é usado porque uma janela "a cada 10 minutos" não cabe nos 15 caracteres da coluna. O conector da fonte MUST responder no contrato do download B3 (change `conector-b3-webhook-ingest`): 200 com a `dataBase` do arquivo e o `idCarga`.

Antes de reivindicar a ocorrência (sem gravar log), a `action` SHALL encerrar se: a ocorrência é anterior ao `inicioHorario`; já existe log de sucesso da tarefa com a data-base de hoje; ou já existe o alerta `CARGA_NAO_RECEBIDA` da tarefa com a data-base de hoje. Os logs de tentativa, sucesso e alerta SHALL trazer a data-base em JSON, para que um reprocessamento manual de outra data não conte como o sucesso de hoje. Nos demais casos, cada ocorrência é uma tentativa:

- 200 com `dataBase` igual à pedida: sucesso, log com o `idCarga`;
- 200 com outra `dataBase` (arquivo de dia anterior), 502, 503, tempo esgotado ou erro de rede: "ainda não recebida", log da tentativa; a próxima ocorrência tenta de novo;
- 400, 401, 403 ou 422: log de erro; a próxima ocorrência tenta de novo (repetir na mesma execução não resolve);
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
- **THEN** o log registra sucesso com o `idCarga`, e as ocorrências seguintes do dia dessa tarefa encerram sem chamar nada

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

### Requirement: `action` de construção da data
O caminho principal da construção é o webhook do processor: cada carga gravada avisa o engine, que constrói na hora as curvas da carga e, em cadeia, as derivadas cujas curvas componentes ficaram completas (changes `engine-construcao-curvas` e `engine-modelos-curva`, spec `curve-load-trigger`). `ConstrucaoDataTaskActionAdapter` (`action` = `construcao-curvas-data`) é a rede de segurança, com a mesma prioridade de construir o quanto antes, para o que o webhook não cobre: engine fora além da janela de repetição do processor, e curva derivada cuja curva componente foi construída à mão pela API (a construção manual não dispara a cadeia).

A execução SHALL chamar `POST {destino=engine}/api/v1/construcoes/{dataBase}`, que constrói toda curva da data com insumo e sem pontos e nunca recalcula. A tarefa SHALL ter `regraIntervalo` (ex.: `PT10M`, ocorrências alinhadas à meia-noite de Brasília) e os parâmetros `inicioHorario` e `limiteHorario` (`HH:mm`, Brasília).

Antes de reivindicar a ocorrência (sem gravar log), a `action` SHALL encerrar se: a ocorrência é anterior ao `inicioHorario`; já existe log de "data completa" da tarefa com a data-base de hoje; ou já existe o alerta `CURVAS_PENDENTES` da tarefa com a data-base de hoje. Nos demais casos:

- tempo esgotado, erro de rede ou 5xx SHALL ser repetidos uma vez na mesma execução, 1 minuto depois (duas chamadas de até 330 s mais a espera ficam em cerca de 12 min, dentro da meta de 15 min); se a segunda também falhar, a execução termina com erro, e a ocorrência seguinte tenta de novo; 4xx MUST encerrar sem repetir;
- com 200, a execução SHALL registrar a quantidade de curvas por situação e por `codigoErro`, com a data-base em JSON. Uma curva é **pendente** se veio com `codigoErro` (exceto `CONSTRUCAO_EM_ANDAMENTO`, que significa que o webhook a está construindo naquele momento) ou com `SEM_INSUMO`; `IGNORADA` e `EXISTENTE` nunca são pendentes. Sem nenhuma pendente, SHALL registrar "data completa", e as ocorrências seguintes do dia não chamam mais o engine;
- ocorrência igual ou posterior ao `limiteHorario`, ainda com pendência (ou com a última chamada do dia falhando): SHALL chamar o engine uma última vez e gravar o alerta `CURVAS_PENDENTES`, listando código, situação e `codigoErro` de cada curva pendente, uma vez por dia. Antes do `limiteHorario`, pendência é esperada (a fonte pode não ter publicado ainda) e MUST NOT gerar alerta.

Uma ocorrência que dispare com a execução anterior ainda em andamento é recusada pela reivindicação e pula, sem execução em paralelo.

#### Scenario: Webhook já construiu
- **WHEN** a carga B3 das 18h40 foi construída pelo webhook, e a ocorrência das 18h50 da construção roda
- **THEN** as curvas da B3 vêm `EXISTENTE`, sem recálculo, e as de fontes que ainda não chegaram vêm `SEM_INSUMO`, sem alerta

#### Scenario: Engine fora além da janela do processor
- **WHEN** o engine ficou fora das 18h30 às 19h35, a janela de aviso do processor esgotou, e o engine volta às 19h35
- **THEN** a ocorrência das 19h40 constrói as curvas da B3 que ficaram sem pontos

#### Scenario: Filha de componente construída à mão
- **WHEN** a carga ANBIMA falhou, e o operador construiu a `NTN-B` pela API às 20h12
- **THEN** a ocorrência das 20h20 constrói a derivada que dependia da `NTN-B`

#### Scenario: Data completa
- **WHEN** a ocorrência das 21h10 não tem nenhuma curva pendente
- **THEN** o log registra "data completa", e as ocorrências seguintes do dia não chamam o engine

#### Scenario: Construção em andamento pelo webhook
- **WHEN** a ocorrência das 19h recebe a `PRE` com `CONSTRUCAO_EM_ANDAMENTO`, porque o webhook está construindo a curva naquele instante
- **THEN** a `PRE` não é pendente, e a ocorrência seguinte a encontra como `EXISTENTE`

#### Scenario: Limite com pendência
- **WHEN** chega a ocorrência do `limiteHorario` (ex.: 23h), a `DPL` vem com `INSUMO_INVALIDO` e a `SOFR` com `SEM_INSUMO`
- **THEN** o alerta `CURVAS_PENDENTES` é gravado uma vez com as duas curvas e os seus códigos, e as ocorrências seguintes do dia não chamam o engine

### Requirement: Alertas no dashboard
`CARGA_NAO_RECEBIDA` e `CURVAS_PENDENTES` SHALL ser gravados como alertas da própria tarefa (requisito "Alertas consultáveis" da spec `agendamento-tarefas`) e SHALL aparecer em `GET /api/v1/alertas`, que o dashboard do front consome. O orquestrador SHALL também incrementar um contador Micrometer por tipo de alerta. Nenhum alerta é enviado por e-mail ou Teams.

#### Scenario: Pendência visível no front
- **WHEN** a última construção do dia grava `CURVAS_PENDENTES`
- **THEN** o alerta aparece na consulta de alertas do dia, com a lista das curvas pendentes

### Requirement: Data-base e dia útil
A data-base de uma execução agendada SHALL ser a data de hoje no fuso `America/Sao_Paulo`, independente do fuso do servidor. O dia útil SHALL ser decidido pelo parâmetro `calendarios` de cada tarefa (requisito "Calendário de dias úteis por tarefa" da spec `agendamento-tarefas`): `Brazil/Settlement` nos downloads B3 e ANBIMA e na construção da data; `Brazil/Settlement,UnitedStates/FederalReserve` no download Bloomberg (a SOFR só tem dado novo em dia útil americano, e a curva só é construída em dia útil brasileiro).

#### Scenario: Servidor em UTC perto da meia-noite
- **WHEN** a ocorrência é às 21h30 de Brasília e o servidor está em UTC
- **THEN** a execução acontece às 21h30 de Brasília, com a data-base do dia em Brasília

#### Scenario: Feriado americano
- **WHEN** chega `2026-11-26` (dia útil no Brasil, Dia de Ação de Graças nos Estados Unidos)
- **THEN** os downloads B3 e ANBIMA e a construção rodam, e o download Bloomberg não

### Requirement: Cadastro das tarefas de curva
As tarefas SHALL ser cadastradas pela API de tarefas (`POST /api/v1/tarefas`, `Curvas.Operador`), com `regraIntervalo` (todas as tarefas de curva tentam de 10 em 10 minutos dentro da sua janela) e os parâmetros da `action`:

| Tarefa | `action` | Parâmetros |
|---|---|---|
| Download B3 | `download-carga-dia` | `fonte`=`B3`, `caminhoDownload`=`/api/b3/taxa-swap/download?date={dataBase}`, `caminhoReprocessamento`=`/api/b3/taxa-swap/reprocessamento?dataBase={dataBase}`, janela |
| Download ANBIMA | `download-carga-dia` | `fonte`=`ANBIMA`, caminhos da rota do conector ANBIMA (change própria do conector), janela |
| Download Bloomberg | `download-carga-dia` | `fonte`=`BLOOMBERG`, caminhos da rota do conector Bloomberg (change própria), janela |
| Construção da data | `construcao-curvas-data` | janela, com o `limiteHorario` depois do `limiteHorario` de todos os downloads |

As tarefas ANBIMA e Bloomberg só são cadastradas quando as rotas do conector existirem. Os cadastros são feitos pela tela; a sugestão de cada um está em `cadastros-sugeridos.txt` desta change. Depois de cadastrada (`PRONTA`), a tarefa SHALL ser agendada (`POST /api/v1/agendador/tarefas/{id}/agendar`) para disparar sozinha. Não há horário embutido no orquestrador: sem cadastro, a tarefa não existe. Mudar horário, intervalo ou limite SHALL ser um `PATCH`, sem redeploy; agendar e desagendar é o liga/desliga do disparo automático.

#### Scenario: Mudar o horário sem redeploy
- **WHEN** um operador faz `PATCH` no `limiteHorario` da `construcao-curvas-data`
- **THEN** a próxima ocorrência já usa o novo limite, sem reiniciar o orquestrador

### Requirement: Execução manual
A execução manual SHALL usar a rota do motor (`POST /api/v1/agendador/tarefas/{id}/executar`, `Curvas.Operador`), aceitando uma `dataBase` opcional (padrão: hoje), inclusive passada ou não útil, e SHALL seguir as regras da `action`, registrando o usuário. Para `download-carga-dia`, uma `dataBase` passada SHALL chamar o `caminhoReprocessamento` da tarefa (se houver; senão, o `caminhoDownload`) em vez do download, e a execução manual é uma tentativa só, sem `inicioHorario` nem `limiteHorario` e sem alerta. Execução manual de qualquer `action` de curva nunca grava alerta.

#### Scenario: Forçar uma data antiga
- **WHEN** o operador executa `construcao-curvas-data` com `dataBase` = `2026-09-10`
- **THEN** o engine constrói as curvas de `2026-09-10` que têm insumo e não têm pontos, e o log registra o usuário e o resultado

### Requirement: Identidade de serviço, correlação e logs
Toda chamada ao conector e ao engine SHALL respeitar o tempo limite do destino (`orquestrador.http.destinos.{destino}.timeout-segundos`; padrão 120 no conector e 330 no engine, acima dos 300 s da construção da data) e levar `Authorization: Bearer` com token do Entra ID por client credentials (Managed Identity ou cofre), com `Curvas.Orquestrador` e `Curvas.Leitura` no engine. Cada execução SHALL ter um `correlationId` (UUID), enviado em `X-Correlation-Id` em toda chamada dela e registrado em todo log dela. Nenhum log MUST conter token nem segredo.

#### Scenario: Papel faltando
- **WHEN** a identidade do orquestrador não tem `Curvas.Orquestrador`
- **THEN** o engine responde 403, a execução encerra com erro sem repetir, e o log mostra a falta de permissão
