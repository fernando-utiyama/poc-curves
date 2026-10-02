## Context

- **Código real transcrito (2026-09-29).** `services/orchestrator` é hexagonal e já usa Java 21 nativo (records nos DTOs, enums). O domínio gira em torno de `Tarefa` (`id`, `nome`, `descricao`, `action`, `regraCron`, `regraIntervalo`, `status`, `parametros`, `logs`, mais datas e execuções), persistida em `tTrefaAgnda`/`tParmTrefa`/`tLogTrefa`. Uma `action` é resolvida por `TaskActionExecutor` a partir do nome cadastrado e delegada a uma implementação de `TaskActionPort`; hoje existem `console` e `http`.
- **Rotas reais.** `TarefaController`/`TarefaCrudService` fazem o CRUD (`/api/v1/tarefas`); `SchedulerController`/`SchedulerService` fazem iniciar/parar/agendar/executar/cancelar/resetar (`/api/v1/agendador/*`). O agendamento físico está atrás da porta `SchedulerPort`, implementada por `SpringSchedulerAdapter` (`ThreadPoolTaskScheduler`).
- **Disparo automático só no `TarefaService`.** Agenda na subida (`ApplicationReadyEvent`) e reconcilia a cada 60 s, desligado por padrão (`scheduler.enabled:false`), e duplica CRUD e execução do fluxo real. `SchedulerService.startAll()` só roda quando alguém chama `/iniciar`.
- **Nada funciona com 2 instâncias.** Agendamento, status e a trava de execução (`synchronized` num `ConcurrentHashMap`) vivem na memória de cada JVM.
- **SSRF** na action `http`, **webhook de notificação sem uso** (repassa o `Authorization` de quem chamou) e **mapeamento trocado** de `action`/`descricao` no `TarefaJpaMapper` (detalhes nas decisões).
- **Colunas disponíveis.** `tTrefaAgnda`: `cIdtfdTrefa`, `cAcaoOperSist`, `cRegraAgnda` (`CHAR(15)`), `cRegraIntvl` (`CHAR(20)`), `cSit` (`CHAR(30)`), `iParmTrefa`, `rTrefa`; sem coluna de data. As colunas `CHAR` voltam do banco completadas com espaços à direita: toda leitura e comparação de situação e de regra (`cSit = 'AGENDADA'`, regra lida igual à regra do disparo) usa o valor sem os espaços (`trim`). `tLogTrefa`: `cIdtfdEntrd`, `cIdtfdTrefa`, `dAtaCriac`, `cSitExcuc`, `rLogTrefa` (`VARCHAR(8000)`, não `MAX`). Nenhuma tarefa cadastrada em nenhum ambiente: o cadastro pode ser limpo.
- **Function B3** (change `conector-b3-webhook-ingest`) e **engine** (change `engine-construcao-curvas`): rotas `download` e `reprocessamento` da function e, no engine, só a exportação de calendário. Nenhum dos dois exige autenticação do orquestrador (o engine está na mesma rede; a function foi liberada como storage owner).
- **Functions ANBIMA e Bloomberg:** o arquivo ANBIMA `ms{AAMMDD}.txt` (produto `MS`) e os nós da SOFR (Bloomberg, `BLC2`, com os tickers vindos do orquestrador) ainda não têm rota; cada uma é uma function própria, no mesmo contrato do download B3, em changes próprias. Depois do download, o fluxo é o da B3: Blob, aviso, processor e webhook do engine.
- **Alternativa guardada.** A versão com o Quartz em cluster está em `alternativa-quartz/` (com o script das tabelas `QRTZ_*`), para adoção futura.

## Goals / Non-Goals

**Goals:** disparo agendado automático, correto com 2+ instâncias (cada disparo roda uma vez, sobrevive à queda de uma instância e ao deploy), status igual em qualquer instância, sem SSRF nem repasse de `Authorization`; as três tarefas das curvas (download B3, ANBIMA e Bloomberg) cadastradas nesse motor, com o alerta no dashboard do front; **nenhuma tabela ou coluna nova**.

**Non-Goals:** lógica de curva, construção da data pelo orquestrador (o engine é disparado pelo processor), a tela do dashboard (front/BFF), as rotas ANBIMA e Bloomberg nas functions (changes próprias), os cadastros em si (feitos pela tela; sugestão em `cadastros-sugeridos.txt`), alteração de schema, e-mail ou Teams.

## Decisions

### D1. Tarefas de curva são `action`, não comportamento fixo do orquestrador
`DownloadCargaTaskActionAdapter` (`download-carga-dia`) implementa `TaskActionPort`, ao lado de `console` e `http`. O motor fica genérico; toda regra de curva mora só nessa classe. O download é uma `action` só para as três fontes (B3 `TaxaSwap`, ANBIMA `ms`, Bloomberg SOFR), cadastrada uma vez por fonte com destino (a function da fonte), caminhos e janela próprios: a regra (tentar até o arquivo do dia chegar, alertar no limite) é a mesma, e a function de cada fonte segue o mesmo contrato de resposta (`dataBase` e `idCarga`). A única diferença é a Bloomberg, que recebe também os `tickers` (D10). O orquestrador não chama o engine para construir: o disparo do engine é da cadeia function → processor → webhook do engine (D15).

### D2. Todas as instâncias agendam; cada disparo tem uma identidade (ocorrência)
Cada instância agenda todas as tarefas no seu `ThreadPoolTaskScheduler`, mas não com `CronTrigger`: o `SpringSchedulerAdapter` calcula a próxima **ocorrência** (o instante programado) pela regra e agenda um disparo único para ela, que ao rodar agenda a seguinte. Assim toda instância sabe exatamente qual ocorrência está disparando, e todas chegam ao mesmo instante pela mesma regra:

- `regraCron`: `org.springframework.scheduling.support.CronExpression.next` (o cron real do Spring, 6 campos, no fuso da JVM, Brasília: D14);
- `regraIntervalo` em duração (ex.: `PT10M`): ocorrências **alinhadas à meia-noite de Brasília** (00h00, 00h10, 00h20…), e não contadas a partir da subida de cada instância, que daria horários diferentes em cada uma;
- `regraIntervalo` em instante: uma ocorrência só.

### D3. Reivindicação da ocorrência numa transação curta
Ao disparar, a instância executa, numa transação de milissegundos:

```sql
SELECT cSit, cRegraAgnda, cRegraIntvl FROM tTrefaAgnda WITH (UPDLOCK, ROWLOCK) WHERE cIdtfdTrefa = @id;
-- (cSit, cRegraAgnda e cRegraIntvl são CHAR: comparar depois de trim)
-- segue só se: cSit = 'AGENDADA' (execução manual: qualquer uma exceto EXECUTANDO, DESABILITADA, REMOVIDA),
-- a regra lida é a mesma que gerou o disparo,
-- e não existe log de início desta ocorrência:
SELECT 1 FROM tLogTrefa WHERE cIdtfdTrefa = @id AND cSitExcuc = 102 AND dAtaCriac >= @ocorrencia;
UPDATE tTrefaAgnda SET cSit = 'EXECUTANDO' WHERE cIdtfdTrefa = @id;
INSERT INTO tLogTrefa (cIdtfdTrefa, dAtaCriac, cSitExcuc, rLogTrefa) VALUES (@id, @agora, 102, '{"ocorrencia": "...", "situacaoOrigem": "AGENDADA", "instancia": "..."}');
```

A trava de linha (`UPDLOCK, ROWLOCK`, mesmo padrão do engine) só serializa a reivindicação: a segunda instância espera os milissegundos da primeira e então enxerga a situação `EXECUTANDO` ou o log de início da ocorrência, e desiste sem erro (log `DEBUG`). A execução da `action` acontece **fora** da transação, então nada fica travado durante a chamada à function ou ao engine. Tempo limite da trava: `orquestrador.execucao.trava-segundos` (padrão 5); estourou, desiste (outra instância está reivindicando).

**Situação ao fim da execução:** se a tarefa saiu de `AGENDADA` e a regra é recorrente, volta para `AGENDADA` **com sucesso ou com erro** (o resultado fica no log); senão, `FINALIZADA` ou `ERRO`. Sem isso, um único erro (ex.: 502 da function) deixaria a tarefa recorrente em `ERRO`, fora da reivindicação, até alguém resetar à mão. A execução manual também volta à situação de origem: no código real ela desligava o agendamento ("execução manual encerra o ciclo"), o que pararia o download diário a cada reprocessamento pela tela.

**Por que a ocorrência, e não só a situação:** travar só por `PRONTA/AGENDADA → EXECUTANDO` impede duas execuções ao mesmo tempo, mas não uma depois da outra: se a instância A roda às 19h00min00s e termina em 2 s, a tarefa volta para `AGENDADA`, e o disparo da instância B às 19h00min03s (atraso de thread, diferença de relógio) passaria. O log de início com `dAtaCriac >= ocorrência` barra esse segundo disparo.

### D4. Disparo perdido, tarefa presa e mudança de cadastro: reconciliação
Um componente de entrada (`adapter/in/scheduler/ReconciliacaoAgendamentos`, chamando só `SchedulerUseCase`) roda na subida e a cada `orquestrador.agendamento.reconciliacao-segundos` (padrão 30):

- **mudança de cadastro:** compara a regra e a situação de cada tarefa no banco com o que esta instância agendou; reagenda o que mudou de regra e cancela o que deixou de estar `AGENDADA` (só `AGENDADA` dispara sozinha; `PRONTA` é cadastrada e parada). A instância que recebe o `PATCH` reagenda na hora; as outras, na próxima reconciliação. Uma ocorrência antiga que ainda dispare numa instância desatualizada é barrada pela D3 (a regra lida não é a do disparo);
- **disparo perdido** (todas fora no horário, ex.: deploy): para cada tarefa `AGENDADA`, a última ocorrência passada dentro de `orquestrador.agendamento.recuperacao-minutos` (padrão 60) sem log de início é disparada uma vez, com a mesma reivindicação da D3 (só uma instância a executa); ocorrências mais antigas que a janela são ignoradas, e ocorrências perdidas em série viram uma execução só. A ocorrência recuperada passa pelas mesmas checagens antes da reivindicação (calendário, e na action de download a janela e o sucesso do dia): o que for dispensado ali não grava log, então a cada reconciliação a última ocorrência passada é reavaliada, o que custa só uma leitura por tarefa;
- **tarefa presa** (instância caiu no meio): `EXECUTANDO` cujo último log de início é mais velho que `orquestrador.execucao.expiracao-minutos` (padrão 15, a meta de duração de uma execução; toda `action` tem de caber nela: o download faz uma chamada de até 120 s), sem log de conclusão ou erro depois, é encerrada como execução com erro ("execução interrompida"): volta para `AGENDADA` se saiu de `AGENDADA` com regra recorrente; senão, vai para `ERRO`.

`/parar` leva todas as `AGENDADA` para `PRONTA`, e `/iniciar`, as `PRONTA` com regra para `AGENDADA`, no banco. `orquestrador.agendamento.habilitado` (padrão `true`) desliga o disparo automático de um ambiente. `TarefaService`, `TarefaUseCase`, `scheduler.enabled` e os mapas em memória de `SchedulerService` e do adaptador são apagados.

### D5. Status sempre do banco
O status vem de `tTrefaAgnda` + `tLogTrefa` (caminho que `RecuperarStatusService` já usa), e a próxima execução é calculada pela regra gravada (D2), igual em qualquer instância.

### D6. Transição de situação atômica
Fora da reivindicação, toda mudança de situação é `UPDATE ... WHERE cIdtfdTrefa = ? AND cSit = <situação esperada>`: zero linhas é conflito, sem sobrescrever (ex.: cancelar numa instância enquanto a outra conclui). A execução manual usa a mesma reivindicação da D3 (ocorrência = agora); com a tarefa `EXECUTANDO`, responde 409, em vez de esperar.

### D7. Sem autenticação no orquestrador
O orquestrador não autentica nem chama as functions e o engine com token: só os serviços que expõem a API ao front (o bff e o `services/curves`) autenticam. O usuário das ações manuais vem do cabeçalho opcional `X-Usuario`, enviado pelo bff, e fica nulo se não vier.

### D8. Action `http` só chama destino cadastrado (corrige SSRF)
Sai o parâmetro `url`; entram `destino` (chave de `orquestrador.http.destinos`, com base-URL) e `caminho`.

### D9. Sem webhook de notificação
O webhook de mudança de tarefa não é usado (desligado por padrão, sem destinatário, e só o `TarefaService`, que sai, o chamava). Apagam-se `WebhookNotifierPort`, `SchedulerWebhookNotifierAdapter`, `SchedulerWebhookClient`, `RestClientSchedulerWebhookClient`, `SchedulerWebhookRequest` e as propriedades `scheduler.webhook.*`, o que elimina também o repasse do `Authorization`. Se um dia houver destinatário, a notificação volta como change própria.

### D10. Download da carga do dia por intervalo alinhado
A coluna de cron tem 15 caracteres, e "a cada 10 minutos das 18h às 21h" não cabe (`0 */10 18-20 * * *` tem 18). Cada tarefa de download (`download-carga-dia`, uma por fonte) chama a function da fonte com a data-base (`{dataBase}` no caminho); na Bloomberg, o parâmetro `tickers` (lista separada por vírgula, editável por `PATCH`) vai no lugar de `{tickers}`, com codificação de URL. Usa `regraIntervalo` = `PT10M` (alinhado, D2) com os parâmetros `inicioHorario` e `limiteHorario`: fora da janela, ou com o arquivo do dia já recebido, ou com o alerta do dia já gravado, a ocorrência encerra **antes** da reivindicação, sem log (para não gravar 144 linhas por dia); dentro da janela, cada ocorrência é uma tentativa; a primeira ocorrência a partir do `limiteHorario` faz a última tentativa e, se o arquivo do dia ainda não veio, grava `CARGA_NAO_RECEBIDA`.

### D11. Correção do mapeamento `action`/`descricao`
`domain.action` passa a vir de `cAcaoOperSist` e `domain.descricao` de `rTrefa`, só no Java. Não há cadastro em nenhum ambiente, então não há dado a migrar.

### D12. Alertas no dashboard do front
`CARGA_NAO_RECEBIDA` é linha de `tLogTrefa` da própria tarefa, com código `500` e texto JSON (`{"alerta": ..., "dataBase": ..., "detalhe": ...}`), bem menor que o limite de `rLogTrefa` (`VARCHAR(8000)`). `GET /api/v1/alertas?dataInicial=&dataFinal=` lista esses registros para o dashboard do front (via BFF). Métricas Micrometer continuam; e-mail e Teams estão fora.

### D13. Calendários iguais aos do engine, por tarefa
O dia útil é uma regra do motor, e não das actions de curva: a tarefa tem o parâmetro opcional `calendarios` (`nome/mercado`, um ou mais), e a ocorrência só executa se hoje (Brasília) for dia útil em todos eles; senão, encerra antes da reivindicação, sem gravar log na tarefa (só log da aplicação), para não gravar uma linha por ocorrência e por instância. B3 e ANBIMA usam `Brazil/Settlement`; o download Bloomberg usa `Brazil/Settlement,UnitedStates/FederalReserve` (a SOFR só tem dado novo em dia útil americano, e a curva só é construída em dia útil brasileiro). Sem o parâmetro, a tarefa roda todo dia.

O orquestrador tem os **mesmos calendários nativos do engine**: as classes `Calendario`, `Brazil`, `UnitedStates` e `CalendarioPorLista` da seção 4 do guia do `engine-construcao-curvas`, copiadas sem alteração de regra. Assim o dia útil não depende do engine no ar (antes, com o engine fora, só o fim de semana era pulado).

- **Referência oficial:** a planilha de feriados nacionais da ANBIMA (`https://www.anbima.com.br/feriados/arqs/feriados_nacionais.xls`, 2001–2099). Conferida em 2026-09-30: a regra nativa do `Brazil`/`Settlement` dá exatamente os mesmos dias úteis que a lista da ANBIMA em todos os anos. A planilha entra nos testes do orquestrador como vetor de conformidade.
- **Feriado decretado:** a fonte continua sendo uma só, o engine (versão importada por planilha, change `engine-modelos-curva`, spec `calendar-management`). O orquestrador lê uma vez por dia a exportação de feriados do engine para cada calendário usado e usa essa lista; com o engine fora, usa a última lista lida (se cobrir o ano) ou o nativo, com o log `CALENDARIO_SEM_SINCRONIA`. O orquestrador não lê a planilha da ANBIMA direto, para não haver duas fontes.
- **Alternativas rejeitadas:** biblioteca compartilhada entre engine e orquestrador (são repositórios separados no banco; exigiria publicar um artefato novo); depender só da exportação do engine (o dia útil pararia com o engine fora).

### D14. Horário do Brasil, data-base e identidade de serviço
Todo horário é o de Brasília. O `main` fixa o fuso padrão da JVM em `America/Sao_Paulo` antes do Spring e recusa subir com outro (mesmo padrão dos demais serviços). O código real hoje mistura quatro fusos: `jackson.time-zone: UTC` no `application.yml`, `America/Sao_Paulo` no cron do `SpringSchedulerAdapter`, `ZoneId.systemDefault()` em `SchedulerService`/`TarefaJpaResponseMapper` e o deslocamento fixo `UTC-3` em `TarefaCrudService`; com o fuso global da JVM, tudo passa a usar o padrão da JVM: somem as constantes de fuso (`APP_ZONE`, `UTC-3`) e o `jackson.time-zone: UTC`, e os instantes da API saem com `-03:00`. A garantia fica num lugar só, o `main` com a checagem na subida, como nos outros serviços. Sem isso, com servidor em UTC, `tLogTrefa.dAtaCriac` ficaria 3 horas deslocado da ocorrência, e a reivindicação da D3 deixaria passar disparo duplicado. Data-base é o dia de hoje em `America/Sao_Paulo`. Chamadas às functions e ao engine respeitam o tempo limite de cada destino (120 s nas functions, 30 s no engine, que só serve o calendário) e não levam token (D7).

### D15. O engine é disparado pela cadeia, não pelo orquestrador
Depois do download, nada mais é do orquestrador: a function grava o original no Blob e avisa, o processor grava o bruto e chama o webhook do engine, que constrói as curvas da carga e, em cadeia, as derivadas cujas curvas componentes ficaram completas (changes `engine-construcao-curvas` e `engine-modelos-curva`, `curve-load-trigger`). O mesmo vale para a ANBIMA e a Bloomberg, que seguem o fluxo da B3. **Alternativa guardada, não adotada:** uma tarefa `construcao-curvas-data` no orquestrador como rede de segurança (a cada 10 minutos, `POST /api/v1/construcoes/{dataBase}`, com o alerta `CURVAS_PENDENTES`), para engine fora além da janela do processor e derivada cuja curva componente foi construída à mão. Quem cobre esses casos hoje é o operador, pela rota de construção do engine.

## Risks / Trade-offs

- **Código de agendamento distribuído próprio.** Reconciliação, recuperação de disparo perdido e de tarefa presa são nossos, com testes nossos; o Quartz (guardado em `alternativa-quartz/`) traria isso pronto, ao custo de 11 tabelas.
- **Mudança de cadastro leva até 30 s para valer nas outras instâncias.** A ocorrência antiga é barrada pela D3; a instância que recebeu a mudança já reagendou.
- **`dAtaCriac` e o relógio.** A comparação com a ocorrência usa o relógio das instâncias (NTP do Azure); `@agora` do log de início é sempre maior ou igual à ocorrência, porque a ocorrência só dispara quando chega.
- **Classes de calendário copiadas do engine.** Uma mudança de regra nativa no engine precisa ser repetida no orquestrador; o teste de conformidade com a planilha da ANBIMA nos dois lados acusa a diferença.
- **Disparos perdidos além da janela de recuperação** são ignorados (60 min por padrão), para não rodar de manhã o que era da noite anterior.
- **Log de início por ocorrência.** Cada tarefa de curva grava, no máximo, as tentativas da sua janela (D10); depois do sucesso do dia (arquivo recebido) ou do alerta, não grava mais nada.
- **Sem rede de segurança para a construção.** Se o engine ficar fora além da janela de repetição do processor, ou uma derivada tiver a componente construída à mão, ninguém reconstrói sozinho: o operador usa a rota de construção do engine (D15).

## Migration Plan

1. Limpar `tTrefaAgnda`/`tParmTrefa`/`tLogTrefa` se houver lixo de teste (nenhum cadastro real).
2. Subir o orquestrador com as decisões D2–D13; nenhum script de banco.
3. Cadastrar pela tela e agendar as tarefas de download (`cadastros-sugeridos.txt`): B3 já; ANBIMA e Bloomberg quando as rotas das functions existirem.
4. Rollback: desabilitar as tarefas pelo cadastro.
5. Se um dia a manutenção do agendamento próprio pesar, adotar a versão guardada em `alternativa-quartz/`.

## Open Questions

- Horários de operação: `inicioHorario`, `limiteHorario` e intervalo de cada download (B3, ANBIMA e Bloomberg).
- Rotas das functions para o arquivo ANBIMA `ms{AAMMDD}.txt` e para os nós da SOFR (changes próprias, ainda não criadas), no mesmo contrato do download B3.
- Como a function Bloomberg recebe os tickers: o desenho assume `{tickers}` no caminho (query, codificada); com muitos tickers a URL pode ficar longa, e então o contrato da function deve aceitar POST com corpo, o que muda só o adaptador.
- Num dia útil no Brasil e feriado nos Estados Unidos, a SOFR não é buscada: o modelo SOFR do engine deve reaproveitar o dado do último dia útil americano, ou a curva fica pendente nesse dia? (regra do engine, não do orquestrador)
