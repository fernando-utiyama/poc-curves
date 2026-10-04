## Purpose

Define o motor genérico de tarefas do orquestrador (`services/orchestrator`): cadastro (CRUD), ciclo de vida, agendamento por cron ou intervalo com várias instâncias, execução extensível por `action`, segurança e observabilidade, sem tabela ou coluna nova. Este motor não conhece curva, B3, ANBIMA nem engine: essas regras vivem só nas `action` cadastradas nele (spec `orquestracao-curvas`).

## ADDED Requirements

### Requirement: Horário do Brasil
Todo horário do orquestrador SHALL ser o de Brasília (`America/Sao_Paulo`), qualquer que seja o fuso do servidor: o `main` SHALL fixar o fuso padrão da JVM em `America/Sao_Paulo` antes de subir o Spring, e a subida MUST falhar se o fuso efetivo for outro (mesmo padrão dos demais serviços, sem classe de relógio própria). Isso vale para: as ocorrências de `regraCron` e o alinhamento de `regraIntervalo` à meia-noite; `inicioHorario` e `limiteHorario`; a data-base; o instante gravado em `tLogTrefa.dAtaCriac` e a sua comparação com a ocorrência na reivindicação; e o calendário de dias úteis. Instantes na API e nos logs JSON SHALL sair com o deslocamento de Brasília (ex.: `2026-09-30T19:00:00-03:00`), e datas sem hora, em `AAAA-MM-DD` da data de Brasília. O fuso é global da JVM: o código SHALL usar o padrão da JVM (`LocalDateTime.now()`, `ZoneId.systemDefault()`, e o Jackson sem `time-zone` próprio), e MUST NOT ter constantes de fuso espalhadas (`ZoneId.of("UTC-3")`, `APP_ZONE`) nem `spring.jackson.time-zone` diferente da JVM; a única garantia é o `main` com a checagem na subida.

#### Scenario: Servidor em UTC
- **WHEN** o servidor está em UTC, e uma tarefa tem ocorrência às 22h de Brasília
- **THEN** ela dispara às 22h de Brasília (01h UTC do dia seguinte), com a data-base do dia em Brasília, e o log de início grava 22h

#### Scenario: Fuso errado na subida
- **WHEN** alguma configuração faz a JVM subir com outro fuso
- **THEN** a aplicação não sobe, e o log diz o fuso encontrado e o esperado

#### Scenario: Resposta da API
- **WHEN** o status de uma tarefa é consultado
- **THEN** a última e a próxima execução vêm com o deslocamento `-03:00`

### Requirement: Cadastro de tarefa
Uma `Tarefa` SHALL ter `nome`, `descricao` e `action` obrigatórios, e no máximo uma regra de agendamento: `regraCron` (cron do Spring, 6 campos, até 15 caracteres) ou `regraIntervalo` (instante ou duração ISO-8601, até 20 caracteres). `action` SHALL ser um nome atendido por algum `TaskActionPort` (`console`, `http`, `carga-download-site`/`carga-data-license`); um nome não atendido MUST falhar com 400. Criar uma tarefa SHALL colocá-la em `PRONTA` e devolver 201 com a localização do recurso. `PATCH` SHALL atualizar só os campos enviados, e MUST recusar alterar uma tarefa `REMOVIDA` ou `EXECUTANDO`. Os campos `action` e `descricao` SHALL ser gravados e lidos cada um na sua coluna, sem inversão.

#### Scenario: Action não atendida
- **WHEN** uma tarefa é criada com `action` = `"ftp-download"`
- **THEN** a criação falha com 400, sem gravar nada

#### Scenario: Cron maior que o permitido
- **WHEN** `regraCron` = `"0 */10 18-20 * * *"` (18 caracteres)
- **THEN** a criação falha por tamanho, antes de qualquer gravação

#### Scenario: Campos lidos como gravados
- **WHEN** uma tarefa é criada com `action` = `"http"` e `descricao` = `"Chama a function"`
- **THEN** a consulta devolve exatamente `action` = `"http"` e `descricao` = `"Chama a function"`

### Requirement: Ciclo de vida
Uma `Tarefa` SHALL ter um dos estados `PRONTA`, `AGENDADA`, `EXECUTANDO`, `FINALIZADA`, `ERRO`, `CANCELADA`, `DESABILITADA` ou `REMOVIDA`, com as transições:

| De | Para |
|---|---|
| `PRONTA` | `AGENDADA`, `DESABILITADA`, `EXECUTANDO` (só execução manual) |
| `AGENDADA` | `PRONTA`, `EXECUTANDO` |
| `EXECUTANDO` | `AGENDADA`, `FINALIZADA`, `ERRO`, `CANCELADA` |
| `FINALIZADA`, `ERRO`, `CANCELADA` | `PRONTA`, `AGENDADA`, `EXECUTANDO` (só execução manual) |
| `DESABILITADA` | `PRONTA`, `REMOVIDA` |
| `REMOVIDA` | nenhuma |

`PRONTA` é cadastrada e parada; só `AGENDADA` dispara sozinha (`/agendar` e `/desagendar` alternam entre as duas). Ao fim de uma execução, a tarefa SHALL voltar à situação de onde saiu se ela era `AGENDADA` e a regra é recorrente (cron ou duração), **com sucesso ou com erro**: o resultado fica no log, e a próxima ocorrência executa normalmente. Nos demais casos (regra de instante, ou execução manual de tarefa parada), SHALL terminar em `FINALIZADA` (sucesso) ou `ERRO`. `CANCELADA` interrompe a execução e para o agendamento, até alguém agendar de novo. A situação de onde a execução saiu SHALL ficar gravada no log de início.

Toda mudança de estado SHALL ser condicionada ao estado esperado: se outra instância mudou o estado antes, a operação MUST falhar com conflito, sem sobrescrever. Uma transição fora da tabela MUST falhar sem alterar o estado. Só uma tarefa `DESABILITADA` SHALL poder ser removida.

#### Scenario: Remover sem desabilitar
- **WHEN** um operador tenta remover uma tarefa `PRONTA`
- **THEN** a operação falha com a transição inválida, e a tarefa continua `PRONTA`

#### Scenario: Erro não para a tarefa recorrente
- **WHEN** uma ocorrência de uma tarefa `AGENDADA` com `regraIntervalo` = `PT10M` termina com erro (a function respondeu 502)
- **THEN** a tarefa volta para `AGENDADA`, o erro fica no log, e a ocorrência seguinte executa

#### Scenario: Duas instâncias mudando o mesmo estado
- **WHEN** a instância A cancela uma tarefa `EXECUTANDO` no mesmo instante em que a instância B a conclui
- **THEN** só uma das mudanças é gravada, e a outra recebe conflito

### Requirement: Agendamento automático em todas as instâncias
Cada instância SHALL agendar todas as tarefas `AGENDADA` (e as `EXECUTANDO` que saíram de `AGENDADA`, para não perder a ocorrência seguinte), na subida e sem chamada a `/iniciar`. Cada disparo SHALL ter uma **ocorrência** (o instante programado) calculada pela regra, igual em todas as instâncias: `regraCron` pelo `CronExpression` do Spring no fuso `America/Sao_Paulo`; `regraIntervalo` em duração com ocorrências alinhadas à meia-noite de Brasília (ex.: `PT10M` → 00h00, 00h10, 00h20…); `regraIntervalo` em instante, uma ocorrência só. A cada `orquestrador.agendamento.reconciliacao-segundos` (padrão 30), a instância SHALL comparar o cadastro do banco com o que agendou, reagendando o que mudou de regra e cancelando o que deixou de estar `AGENDADA`; a instância que recebe a mudança pela API SHALL reagendar na hora. `orquestrador.agendamento.habilitado` (padrão `true`) desliga o disparo automático de um ambiente. `/api/v1/agendador/parar` SHALL levar todas as tarefas `AGENDADA` para `PRONTA`, e `/iniciar`, todas as `PRONTA` com regra para `AGENDADA`, no banco (e não só na instância que recebeu a chamada), de modo que todas as instâncias obedeçam na próxima reconciliação.

#### Scenario: Reinício sem chamada manual
- **WHEN** as instâncias reiniciam num deploy
- **THEN** as tarefas `AGENDADA` voltam a disparar nos horários, sem ninguém chamar `/iniciar`

#### Scenario: Tarefa cadastrada e não agendada
- **WHEN** uma tarefa com `regraCron` é criada e fica `PRONTA`
- **THEN** ela não dispara até alguém chamar `/agendar`

#### Scenario: Intervalo alinhado
- **WHEN** a instância A sobe às 10h03 e a instância B às 10h07, e uma tarefa tem `regraIntervalo` = `PT10M`
- **THEN** as duas disparam a ocorrência das 10h10 (e não 10h13 e 10h17)

#### Scenario: Cadastro alterado numa instância
- **WHEN** um `PATCH` muda o `regraCron` de uma tarefa pela instância A
- **THEN** a instância A reagenda na hora, e a instância B, na próxima reconciliação

### Requirement: Execução única por ocorrência
Com 2 ou mais instâncias, cada ocorrência SHALL ser executada por uma só instância, inclusive quando as instâncias disparam com alguns segundos de diferença. Antes de executar, a instância SHALL reivindicar a ocorrência numa transação curta com trava de linha em `tTrefaAgnda` (`UPDLOCK, ROWLOCK`, tempo limite `orquestrador.execucao.trava-segundos`, padrão 5): só segue se a tarefa estiver `AGENDADA`, se a regra gravada for a mesma que gerou o disparo, e se não houver log de início (código `102`) com instante igual ou posterior à ocorrência; então muda a situação para `EXECUTANDO` e grava o log de início (ocorrência, situação de origem, instância), na mesma transação. Quem não reivindica desiste sem erro. As colunas `cSit`, `cRegraAgnda` e `cRegraIntvl` são `CHAR` e voltam do banco com espaços à direita: a situação e a regra lidas MUST ser comparadas sem esses espaços. A execução da `action` SHALL acontecer fora dessa transação.

#### Scenario: Duas instâncias no mesmo instante
- **WHEN** as duas instâncias disparam a mesma ocorrência ao mesmo tempo
- **THEN** a tarefa executa uma vez, e a outra instância não grava erro

#### Scenario: Segunda instância atrasada
- **WHEN** a instância A executa a ocorrência das 19h e termina às 19h00min02s, e a instância B dispara a mesma ocorrência às 19h00min03s
- **THEN** a instância B encontra o log de início da ocorrência das 19h e não executa

#### Scenario: Instância com o cadastro antigo
- **WHEN** a instância B ainda não reconciliou um `PATCH` e dispara uma ocorrência da regra antiga
- **THEN** a reivindicação falha porque a regra gravada é outra, e nada executa

### Requirement: Recuperação de disparo perdido e de tarefa presa
Na subida e em cada reconciliação, para cada tarefa `AGENDADA`, a última ocorrência passada dentro de `orquestrador.agendamento.recuperacao-minutos` (padrão 60) sem log de início SHALL ser disparada uma vez, com a mesma reivindicação; várias ocorrências perdidas viram uma execução só, e ocorrências mais antigas que a janela são ignoradas. A ocorrência recuperada passa pelas mesmas checagens antes da reivindicação (calendário e, na `action`, o que ela decide dispensar sem log), então a dispensada não executa nem grava log. Uma tarefa `EXECUTANDO` cujo último log de início é mais velho que `orquestrador.execucao.expiracao-minutos` (padrão 15, a meta de duração de uma execução), sem log de conclusão ou erro depois, SHALL ser encerrada como uma execução com erro (log "execução interrompida"): volta para `AGENDADA` se saiu de `AGENDADA` com regra recorrente; senão, vai para `ERRO`.

#### Scenario: Deploy no horário do disparo
- **WHEN** as duas instâncias estão fora das 18h55 às 19h10, e a tarefa tinha disparo às 19h
- **THEN** a ocorrência das 19h executa uma vez quando a primeira instância volta

#### Scenario: Instância cai no meio
- **WHEN** a instância A cai enquanto executa uma tarefa `AGENDADA` recorrente, e passam 15 minutos
- **THEN** a tarefa volta para `AGENDADA` com o log "execução interrompida", e a próxima ocorrência executa normalmente

### Requirement: Status a partir do banco
O status de uma tarefa (`GET .../tarefas/{id}/status`) e o status global (`GET .../status`) SHALL vir da situação e dos logs gravados, e a próxima execução, da regra gravada; nunca de estado guardado só na memória da instância que respondeu. A mesma consulta em instâncias diferentes SHALL devolver o mesmo resultado.

#### Scenario: Consultar noutra instância
- **WHEN** a tarefa X executa na instância A, e o status dela é consultado na instância B
- **THEN** a instância B devolve a mesma situação, última execução, próxima execução e última mensagem

### Requirement: Calendário de dias úteis por tarefa
Uma tarefa SHALL poder ter o parâmetro opcional `calendarios` (um ou mais `nome/mercado`, separados por vírgula, ex.: `Brazil/Settlement` ou `Brazil/Settlement,UnitedStates/FederalReserve`). Numa ocorrência agendada, se a data de hoje em `America/Sao_Paulo` não for dia útil em **todos** os calendários da tarefa, a ocorrência SHALL encerrar antes da reivindicação, sem chamar a `action` e sem gravar log na tarefa (só log da aplicação `TAREFA_PULADA`, com o motivo e o calendário), para não repetir uma linha por ocorrência e por instância. A execução manual não é pulada pelo calendário (ele só conta a defasagem da data-base padrão). Sem o parâmetro, a tarefa roda todo dia.

O orquestrador SHALL ter os mesmos calendários nativos do engine (`Brazil`/`Settlement` e `UnitedStates`/`FederalReserve`, com as mesmas regras de feriado e Páscoa da change `engine-construcao-curvas`), sem depender do engine no ar. Para acompanhar os feriados decretados (versões importadas por planilha no engine), o orquestrador SHALL ler uma vez por dia, por calendário usado, a exportação de feriados do engine (`GET {destino=engine}/api/v1/calendarios/{nome}?mercado={mercado}&anoInicial={ano}&anoFinal={ano+1}&formato=json`, sem autenticação) e usar essa lista enquanto valer; com o engine fora, SHALL usar o calendário nativo, com log `CALENDARIO_SEM_SINCRONIA` em nível `AVISO`. Um calendário não conhecido no parâmetro MUST falhar no cadastro com 400.

#### Scenario: Feriado nacional
- **WHEN** uma ocorrência da tarefa com `calendarios` = `Brazil/Settlement` cai em `2026-11-20`
- **THEN** a `action` não é chamada, nada é gravado na tarefa, e o log da aplicação registra `TAREFA_PULADA`

#### Scenario: Feriado só americano
- **WHEN** uma ocorrência da tarefa com `calendarios` = `Brazil/Settlement,UnitedStates/FederalReserve` cai em `2026-11-26` (Dia de Ação de Graças nos Estados Unidos, dia útil no Brasil)
- **THEN** a `action` não é chamada

#### Scenario: Feriado decretado com o engine fora
- **WHEN** o engine tem um feriado decretado importado ontem, e está fora na hora da sincronização
- **THEN** o orquestrador usa a última lista sincronizada, se ainda valer para o ano; senão, o nativo, com `CALENDARIO_SEM_SINCRONIA`

#### Scenario: Mesmos dias úteis do engine e da ANBIMA
- **WHEN** o `Brazil`/`Settlement` do orquestrador é comparado, de 2001 a 2099, com o do engine e com a planilha de feriados nacionais da ANBIMA (`feriados_nacionais.xls`)
- **THEN** os três consideram úteis exatamente os mesmos dias

### Requirement: Execução manual
`POST /api/v1/agendador/tarefas/{id}/executar` SHALL reivindicar uma ocorrência igual a agora, pela mesma regra de execução única (aceitando qualquer situação exceto `EXECUTANDO`, `DESABILITADA` e `REMOVIDA`), e aceitar parâmetros de execução definidos pela `action` (ex.: `dataBase`). Com a tarefa `EXECUTANDO`, SHALL responder 409, sem esperar; `DESABILITADA` ou `REMOVIDA`, 400. Ao fim, a tarefa SHALL voltar à situação de onde saiu (uma `AGENDADA` continua `AGENDADA`: a execução manual não desliga o agendamento). A execução manual não consulta o calendário da tarefa e registra o usuário no log.

#### Scenario: Manual durante a execução agendada
- **WHEN** um operador pede `/executar` de uma tarefa que está executando pelo agendamento
- **THEN** a resposta é 409, e a execução em andamento segue

#### Scenario: Manual não desliga o agendamento
- **WHEN** um operador executa manualmente uma tarefa `AGENDADA`
- **THEN** ao fim ela volta para `AGENDADA`, e as próximas ocorrências continuam

### Requirement: Extensibilidade por `action`
Uma nova `action` SHALL poder ser adicionada implementando `TaskActionPort` (`getActionName`, `getSupportedActionNames`, `execute`), sem alterar cadastro, agendamento ou execução única. Uma `action` MUST validar os próprios parâmetros e falhar com erro claro quando um obrigatório faltar ou for inválido. Uma `action` MUST terminar dentro da meta de duração (`orquestrador.execucao.expiracao-minutos`, 15 min), contando tempos limite de chamada e novas tentativas dentro da execução; o que passar disso é tratado como execução interrompida. Uma `action` SHALL poder decidir, antes da reivindicação, que uma ocorrência não precisa executar (ex.: fora da janela), encerrando sem log.

#### Scenario: Parâmetro obrigatório ausente
- **WHEN** uma tarefa com `action` = `"http"` não tem o parâmetro `destino`
- **THEN** a execução falha com erro claro sobre o parâmetro, sem nenhuma chamada

### Requirement: Rotas sem autenticação própria
O orquestrador MUST NOT autenticar chamadas: quem expõe a API ao usuário (o bff e o `services/curves`) autentica. O usuário das ações manuais (executar, cancelar, resetar e as mutações de tarefa) SHALL vir do cabeçalho opcional `X-Usuario` e ficar nulo se ele não vier.

#### Scenario: Execução sem cabeçalho de usuário
- **WHEN** `POST /api/v1/agendador/tarefas/{id}/executar` chega sem `X-Usuario`
- **THEN** a tarefa executa normalmente, com o usuário nulo no registro

### Requirement: Chamada de saída restrita a destino cadastrado
A `action` `http` MUST NOT aceitar URL livre. Ela SHALL receber `destino` (chave de `orquestrador.http.destinos`, cada uma com base-URL) e `caminho` (sem esquema nem host); `destino` não cadastrado MUST falhar sem chamar nada.

#### Scenario: Destino não cadastrado
- **WHEN** a tarefa tem `destino` = `"externo-desconhecido"`
- **THEN** a execução falha antes de qualquer chamada de rede

### Requirement: Sem webhook de notificação
O orquestrador MUST NOT notificar webhook algum sobre mudança de tarefa: a porta `WebhookNotifierPort`, o adaptador, o cliente e as propriedades `scheduler.webhook.*` não existem. O que acontece com uma tarefa fica em `tLogTrefa`, nos status e em `GET /api/v1/alertas`.

#### Scenario: Execução via API
- **WHEN** um operador executa uma tarefa
- **THEN** nenhuma requisição de notificação sai do orquestrador, além da chamada da própria `action`

### Requirement: Alertas consultáveis
Um alerta de tarefa SHALL ser gravado em `tLogTrefa` da própria tarefa, com código `500` e texto JSON com os campos `alerta` (tipo), `dataBase` e `detalhe`. `GET /api/v1/alertas?dataInicial=&dataFinal=` SHALL devolver os alertas do período, cada um com tarefa (id e nome), tipo, data-base, instante e detalhe, do mais recente para o mais antigo, para o dashboard do front.

#### Scenario: Dashboard lista os alertas do dia
- **WHEN** o front consulta os alertas de `2026-09-14`
- **THEN** recebe cada alerta registrado nessa data, com tarefa, tipo e detalhe
