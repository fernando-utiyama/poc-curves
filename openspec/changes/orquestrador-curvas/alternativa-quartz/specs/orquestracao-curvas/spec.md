## Purpose

Define o que o orquestrador faz no processo das curvas: disparar o download do `TaxaSwap` da B3 no conector e a construção automática da data no engine, tentar de novo quando o arquivo do dia ainda não saiu, alertar no dashboard o que não aconteceu e permitir o disparo manual de uma data. As duas coisas são tarefas cadastradas no motor genérico (spec `agendamento-tarefas`), cada uma com a sua `action`; o orquestrador não calcula nem interpreta curvas.

## ADDED Requirements

### Requirement: `action` de download B3
`B3DownloadTaskActionAdapter` (`action` = `b3-taxa-swap-download`) SHALL chamar `POST {destino=conector}/api/b3/taxa-swap/download?date={dataBase}`. A tarefa SHALL ter `regraCron` com o início da janela (ex.: `0 0 18 * * ?`) e os parâmetros `intervaloMinutos` e `limiteHorario` (`HH:mm`, Brasília).

Cada execução é uma tentativa:

- já existe log de sucesso de hoje para a tarefa: encerra sem chamar o conector;
- 200 com `dataBase` igual à pedida: sucesso, log com o `idCarga`;
- 200 com outra `dataBase` (busca de dias anteriores do conector), 502, 503, tempo esgotado ou erro de rede: "ainda não recebida"; se agora + `intervaloMinutos` for antes do `limiteHorario`, a action SHALL pedir ao `SchedulerPort` um gatilho único de nova tentativa nesse instante; senão, SHALL gravar o alerta `CARGA_NAO_RECEBIDA` (data-base, quantidade de tentativas de hoje, última resposta), uma vez por dia;
- 400, 401, 403 ou 422: encerra com erro, sem nova tentativa, porque repetir não resolve.

#### Scenario: B3 ainda não publicou
- **WHEN** a tentativa das 18h para `2026-09-14` recebe 200 com `dataBase` = `2026-09-11`, com `intervaloMinutos` = 10 e `limiteHorario` = `21:00`
- **THEN** a tentativa é registrada, e uma nova tentativa fica agendada para 18h10

#### Scenario: Arquivo do dia recebido
- **WHEN** a tentativa das 18h40 recebe 200 com `dataBase` = `2026-09-14`
- **THEN** o log registra sucesso com o `idCarga`, e nenhuma nova tentativa é agendada

#### Scenario: Horário limite sem sucesso
- **WHEN** a tentativa das 20h55 também não traz o arquivo do dia, e a próxima cairia depois das 21h
- **THEN** o alerta `CARGA_NAO_RECEBIDA` é gravado, e nenhuma nova tentativa é agendada

#### Scenario: Uma instância cai entre as tentativas
- **WHEN** a instância que agendou a tentativa das 18h10 cai às 18h05
- **THEN** a tentativa das 18h10 roda na outra instância

### Requirement: `action` de construção da data
`ConstrucaoDataTaskActionAdapter` (`action` = `construcao-curvas-data`) SHALL chamar `POST {destino=engine}/api/v1/construcoes/{dataBase}`, que nunca recalcula. Tempo esgotado, erro de rede ou 5xx SHALL ser repetidos até 3 vezes na mesma execução, com 1, 2 e 4 minutos de espera; 4xx MUST encerrar sem repetir. Com 200, a execução SHALL registrar a quantidade de curvas por situação e por `codigoErro`. A tarefa SHALL ter `regraCron` com os horários do dia (ex.: `0 0 19-21 * * ?`).

A execução SHALL verificar, pela expressão cron da tarefa, se não há outro disparo antes da meia-noite (é a última do dia). Se for a última e houver curva com `codigoErro`, com `SEM_INSUMO`, ou se a chamada tiver falhado, SHALL gravar o alerta `CURVAS_PENDENTES`, listando código, situação e `codigoErro` de cada curva pendente. Fora da última execução do dia, `SEM_INSUMO` é esperado e MUST NOT gerar alerta. Curva `IGNORADA` nunca é pendente.

#### Scenario: Primeira rodada antes da carga
- **WHEN** a execução das 19h de `2026-09-14` não é a última do dia, e as curvas ANBIMA e Bloomberg vêm `SEM_INSUMO`
- **THEN** não há alerta

#### Scenario: Última rodada com pendência
- **WHEN** a execução das 21h é a última do dia, a `DPL` vem com `INSUMO_INVALIDO` e a `SOFR` com `SEM_INSUMO`
- **THEN** o alerta `CURVAS_PENDENTES` é gravado com as duas curvas e os seus códigos

### Requirement: Alertas no dashboard
`CARGA_NAO_RECEBIDA` e `CURVAS_PENDENTES` SHALL ser gravados como alertas da própria tarefa (requisito "Alertas consultáveis" da spec `agendamento-tarefas`) e SHALL aparecer em `GET /api/v1/alertas`, que o dashboard do front consome. O orquestrador SHALL também incrementar um contador Micrometer por tipo de alerta. Nenhum alerta é enviado por e-mail ou Teams.

#### Scenario: Pendência visível no front
- **WHEN** a última construção do dia grava `CURVAS_PENDENTES`
- **THEN** o alerta aparece na consulta de alertas do dia, com a lista das curvas pendentes

### Requirement: Data-base e dia útil
A data-base de uma execução agendada SHALL ser a data de hoje no fuso `America/Sao_Paulo`, independente do fuso do servidor. Se ela não for dia útil no calendário `Brazil`/`Settlement`, a execução MUST NOT chamar o conector nem o engine, e SHALL registrar `TAREFA_PULADA` com o motivo; por isso o `regraCron` pode ser de todos os dias. Os feriados SHALL vir de `GET {destino=engine}/api/v1/calendarios/Brazil?mercado=Settlement&anoInicial={ano}&anoFinal={ano}`, guardados em memória até o fim do dia; com o engine fora, só sábado e domingo SHALL ser pulados, com `CALENDARIO_INDISPONIVEL`.

#### Scenario: Feriado
- **WHEN** um disparo cai em `2026-11-20` (feriado no `Brazil`/`Settlement`)
- **THEN** nada é chamado, e o log registra `TAREFA_PULADA`

#### Scenario: Servidor em UTC perto da meia-noite
- **WHEN** o gatilho dispara às 21h30 de Brasília e o servidor está em UTC
- **THEN** a execução acontece às 21h30 de Brasília, com a data-base do dia em Brasília

### Requirement: Cadastro das tarefas de curva
As duas tarefas SHALL ser cadastradas pela API de tarefas (`POST /api/v1/tarefas`, `Curvas.Operador`), com `regraCron` e os parâmetros de cada `action`. Não há horário embutido no orquestrador: sem cadastro, a tarefa não existe. Mudar horário, intervalo ou limite SHALL ser um `PATCH`, sem redeploy; desabilitar e habilitar pelo cadastro é o liga/desliga.

#### Scenario: Mudar o horário sem redeploy
- **WHEN** um operador faz `PATCH` no `regraCron` da `construcao-curvas-data`
- **THEN** o próximo disparo segue o novo horário, sem reiniciar o orquestrador

### Requirement: Execução manual
A execução manual SHALL usar a rota do motor (`POST /api/v1/agendador/tarefas/{id}/executar`, `Curvas.Operador`), aceitando uma `dataBase` opcional (padrão: hoje), inclusive passada ou não útil, e SHALL seguir as regras da `action`, registrando o usuário. Para o download B3, uma `dataBase` passada SHALL chamar `POST {destino=conector}/api/b3/taxa-swap/reprocessamento?dataBase={data}` em vez do download, e a execução manual MUST NOT agendar novas tentativas.

#### Scenario: Forçar uma data antiga
- **WHEN** o operador executa `construcao-curvas-data` com `dataBase` = `2026-09-10`
- **THEN** o engine constrói as curvas de `2026-09-10` que têm insumo e não têm pontos, e o log registra o usuário e o resultado

### Requirement: Identidade de serviço, correlação e logs
Toda chamada ao conector e ao engine SHALL levar `Authorization: Bearer` com token do Entra ID por client credentials (Managed Identity ou cofre), com `Curvas.Orquestrador` e `Curvas.Leitura` no engine. Cada execução SHALL ter um `correlationId` (UUID), enviado em `X-Correlation-Id` em toda chamada dela e registrado em todo log dela. Nenhum log MUST conter token nem segredo.

#### Scenario: Papel faltando
- **WHEN** a identidade do orquestrador não tem `Curvas.Orquestrador`
- **THEN** o engine responde 403, a execução encerra com erro sem repetir, e o log mostra a falta de permissão
