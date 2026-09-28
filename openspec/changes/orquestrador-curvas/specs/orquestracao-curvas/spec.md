## Purpose

Define o que o orquestrador (`services/orchestrator`) faz no processo das curvas: disparar o download do `TaxaSwap` da B3 no conector e a construção automática da data no engine, nos horários configurados, repetir quando o arquivo do dia ainda não saiu, alertar o que não aconteceu e permitir o disparo manual de uma data. O orquestrador não calcula nem interpreta curvas.

## ADDED Requirements

### Requirement: Tarefas das curvas
O orquestrador SHALL ter as tarefas:

| Tarefa | Chamada | Sucesso |
|---|---|---|
| `B3_TAXA_SWAP_DOWNLOAD` | `POST {conector}/api/b3/taxa-swap/download?date={dataBase}` | 200 com `dataBase` igual à pedida |
| `CONSTRUCAO_CURVAS_DATA` | `POST {engine}/api/v1/construcoes/{dataBase}` | 200 (o resultado de cada curva é avaliado à parte) |

Cada tarefa SHALL ser agendada nos horários de `orquestrador.curvas.{tarefa}.horarios` (lista de `HH:mm`, horário de Brasília). Não há horário padrão: uma tarefa sem horários configurados MUST NOT ser agendada, e a subida SHALL registrar `TAREFA_SEM_HORARIO` com nível `ERRO`. Os endereços do conector e do engine vêm de `orquestrador.curvas.conector-url` e `orquestrador.curvas.engine-url`.

#### Scenario: Tarefa sem horário
- **WHEN** o orquestrador sobe sem `orquestrador.curvas.CONSTRUCAO_CURVAS_DATA.horarios`
- **THEN** a tarefa não é agendada, o log tem `TAREFA_SEM_HORARIO`, e a execução manual dela continua disponível

### Requirement: Data-base e dia útil
A data-base de um disparo agendado SHALL ser a data de hoje no fuso `America/Sao_Paulo`, independente do fuso do servidor. Se a data-base não for dia útil no calendário `Brazil`/`Settlement`, o disparo MUST NOT chamar o conector nem o engine, e SHALL registrar `TAREFA_PULADA` com o motivo. Os feriados SHALL vir de `GET {engine}/api/v1/calendarios/Brazil?mercado=Settlement&anoInicial={ano}&anoFinal={ano}`, guardados em memória até o fim do dia. Se o engine não responder, só sábado e domingo SHALL ser pulados, com `CALENDARIO_INDISPONIVEL` com nível `AVISO`.

#### Scenario: Feriado
- **WHEN** chega o horário do download em `2026-11-20` (feriado no `Brazil`/`Settlement`)
- **THEN** nada é chamado, e o log tem `TAREFA_PULADA` com o motivo feriado

#### Scenario: Servidor em UTC perto da meia-noite
- **WHEN** o horário configurado é `21:30` e o servidor está em UTC
- **THEN** o disparo acontece às 21h30 de Brasília, com a data-base do dia em Brasília

### Requirement: Download B3 com novas tentativas
A tarefa `B3_TAXA_SWAP_DOWNLOAD` SHALL considerar a carga do dia recebida só quando o conector responder 200 com `dataBase` igual à data pedida. Resposta 200 com outra `dataBase` (a busca de dias anteriores do conector trouxe um arquivo antigo), 502, 503, tempo esgotado ou erro de rede SHALL ser tratados como "ainda não recebida": a tarefa SHALL tentar de novo a cada `orquestrador.curvas.B3_TAXA_SWAP_DOWNLOAD.intervalo-minutos`, até receber ou até o horário `orquestrador.curvas.B3_TAXA_SWAP_DOWNLOAD.limite` (`HH:mm`, Brasília). Resposta 400, 401, 403 ou 422 MUST encerrar as tentativas, com `TAREFA_FALHOU` com nível `ERRO`, porque repetir não resolve.

Chegado o limite sem receber, o orquestrador SHALL registrar `CARGA_NAO_RECEBIDA` com nível `ERRO` (tarefa, data-base, tentativas, última resposta) e incrementar a métrica de alerta. O tempo limite de cada chamada ao conector SHALL ser `orquestrador.curvas.conector-timeout-segundos` (padrão 120).

#### Scenario: B3 ainda não publicou
- **WHEN** o download de `2026-09-14` é chamado às 19h, e o conector devolve 200 com `dataBase` = `2026-09-11` (arquivo do pregão anterior)
- **THEN** a tarefa não considera a carga recebida e tenta de novo depois do intervalo configurado

#### Scenario: Arquivo do dia recebido
- **WHEN** uma nova tentativa devolve 200 com `dataBase` = `2026-09-14`
- **THEN** a tarefa termina com `TAREFA_CONCLUIDA`, com o `idCarga`, e não tenta mais naquele dia

#### Scenario: Limite atingido
- **WHEN** chega o horário limite e nenhuma tentativa trouxe o arquivo de `2026-09-14`
- **THEN** o log tem `CARGA_NAO_RECEBIDA` com a data, a quantidade de tentativas e a última resposta

### Requirement: Construção da data
A tarefa `CONSTRUCAO_CURVAS_DATA` SHALL chamar a construção automática da data no engine, que nunca recalcula e pode ser chamada quantas vezes for preciso. Com resposta 200, SHALL registrar `TAREFA_CONCLUIDA` com a quantidade de curvas por situação e por código de erro. Tempo esgotado, erro de rede ou 5xx SHALL ser repetidos até 3 vezes, com 1, 2 e 4 minutos de espera; 4xx MUST encerrar com `TAREFA_FALHOU`. O tempo limite da chamada SHALL ser `orquestrador.curvas.engine-timeout-segundos` (padrão 330, maior que os 300 segundos do engine).

No **último horário configurado do dia**, se a resposta tiver alguma curva com `codigoErro`, com situação `SEM_INSUMO`, ou se a chamada tiver falhado, o orquestrador SHALL registrar `CURVAS_PENDENTES` com nível `ERRO`, listando código, situação e `codigoErro` de cada curva pendente, e incrementar a métrica de alerta. Nos horários anteriores, `SEM_INSUMO` é esperado e MUST NOT gerar alerta. Curvas `IGNORADA` nunca são pendentes.

#### Scenario: Primeira rodada antes da carga
- **WHEN** a construção de `2026-09-14` roda às 19h, antes das cargas ANBIMA e Bloomberg
- **THEN** as curvas dessas fontes vêm como `SEM_INSUMO`, e não há alerta

#### Scenario: Última rodada com pendência
- **WHEN** na última rodada do dia a `DPL` vem com `INSUMO_INVALIDO` e a `SOFR` com `SEM_INSUMO`
- **THEN** o log tem `CURVAS_PENDENTES` listando `DPL` com `INSUMO_INVALIDO` e `SOFR` com `SEM_INSUMO`

### Requirement: Execução manual
O orquestrador SHALL permitir executar qualquer tarefa das curvas para uma data informada (`AAAA-MM-DD`), inclusive passada ou não útil, por usuário com o papel `Curvas.Operador`, e SHALL permitir pedir o reprocessamento B3 de uma data, chamando `POST {conector}/api/b3/taxa-swap/reprocessamento?dataBase={data}`. A execução manual SHALL seguir as mesmas regras de novas tentativas, sem horário limite (uma única rodada de tentativas pelo número de repetições), e SHALL registrar o usuário. A forma de expor a execução manual (rota, tela) segue a estrutura do orquestrador real.

#### Scenario: Forçar uma data antiga
- **WHEN** o operador executa `CONSTRUCAO_CURVAS_DATA` para `2026-09-10`
- **THEN** o engine constrói as curvas de `2026-09-10` que têm insumo e não têm pontos, e o log tem o usuário e o resultado

### Requirement: Identidade de serviço
Toda chamada ao conector e ao engine SHALL levar `Authorization: Bearer` com token do Entra ID obtido por client credentials, com a identidade de serviço do orquestrador. No engine, essa identidade SHALL ter os papéis `Curvas.Orquestrador` (construção da data) e `Curvas.Leitura` (calendário); no conector, o `appid` dela é o configurado em `B3_ORQUESTRADOR_APP_ID`. Segredos MUST NOT ficar em configuração de produção: a credencial vem de Managed Identity ou do cofre de chaves.

#### Scenario: Papel faltando
- **WHEN** a identidade do orquestrador não tem `Curvas.Orquestrador`
- **THEN** o engine responde 403, a tarefa termina com `TAREFA_FALHOU` sem repetir, e o log mostra `SEM_PERMISSAO`

### Requirement: Execução única e idempotência
O orquestrador roda em no mínimo duas instâncias no Azure, e cada disparo agendado (tarefa, data-base, horário) SHALL ser executado por uma só instância. As chamadas feitas SHALL ser idempotentes por construção: repetir o download da mesma data gera o mesmo `idCarga`, e repetir a construção da data não reconstrói curva com pontos. O orquestrador MUST NOT criar tópico, fila ou tabela para as tarefas das curvas.

#### Scenario: Disparo duplicado
- **WHEN** por falha no controle de execução única duas instâncias disparam a construção de `2026-09-14`
- **THEN** o engine constrói cada curva uma vez, e a outra chamada recebe as curvas como `EXISTENTE`

### Requirement: Correlação, logs e métricas
Cada execução de tarefa SHALL ter um `correlationId` (UUID), enviado no cabeçalho `X-Correlation-Id` de toda chamada daquela execução, e SHALL registrar em log JSON, com o fuso de Brasília nos instantes: `TAREFA_INICIADA` (tarefa, data-base, `acionadoPor` `AGENDA` ou `MANUAL`, usuário), `TAREFA_TENTATIVA` (tentativa, status HTTP, `codigoErro`, duração), `TAREFA_CONCLUIDA`, `TAREFA_FALHOU`, `TAREFA_PULADA`, `CARGA_NAO_RECEBIDA` e `CURVAS_PENDENTES`. O log MUST NOT conter token nem segredo. O orquestrador SHALL publicar pelo Micrometer contadores de execuções por tarefa e resultado, de `CARGA_NAO_RECEBIDA` e de `CURVAS_PENDENTES`, e a duração das chamadas por destino.

#### Scenario: Rastreio ponta a ponta
- **WHEN** o download de `2026-09-14` é executado
- **THEN** o mesmo `correlationId` aparece no log do orquestrador e no do conector daquela chamada
