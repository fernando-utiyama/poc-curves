## Context

A motivação está no proposal e o comportamento na spec. Estado atual do `services/orchestrator` transcrito:

- `HttpTaskActionAdapter` chama qualquer `url` vinda do parâmetro da tarefa, com cabeçalhos livres (`header.*`): é a falha de SSRF que a v1 corrige na tarefa 1.7.
- A execução manual (`SchedulerService.executeTask`) usa um mapa em memória (`runningTasks`) para saber o que está rodando: não vale entre instâncias.
- `jackson.time-zone: UTC` no `application.yml` e constantes de fuso espalhadas; `TarefaJpaMapper` troca `action` e `descricao`.
- Nenhuma `action` de download existe; o processor emergencial v0 expõe as rotas de download e reprocessamento das três fontes no contrato das functions.

## Goals / Non-Goals

**Goals:**
- Disparar manualmente as três tarefas de download (hoje ou data passada) e ver o resultado.
- Fazer agora, do jeito da v1, o que a v1 também precisa: fuso, mapper, destino cadastrado, cliente de saída, reivindicação no banco, `action` de download.

**Non-Goals (ficam para a v1, `orquestrador-curvas`):**
- Agendamento: ocorrências alinhadas, reivindicação por ocorrência, reconciliação, recuperação, expiração de execução.
- Janela (`inicioHorario`, `limiteHorario`), repetição a cada 10 minutos, alerta `CARGA_NAO_RECEBIDA`, `GET /api/v1/alertas`.
- Calendários e sincronização de feriados.
- Remoção de `TarefaService`, webhook de notificação e `scheduler.*`.
- Tela nova: a execução manual usa a rota que o front e o bff já chamam para executar tarefa.

## Decisions

### D1. Cada peça da v0 é a primeira parte de uma tarefa da v1
| v0 | v1 |
|---|---|
| fuso de Brasília | 1.0 inteira |
| mapper | 1.10 inteira |
| destino + caminho, cliente sem autenticação com correlação | 1.7 e 2.2 inteiras |
| `DownloadCargaTaskActionAdapter`, modo manual | 2.1, sem a janela, o encerramento antes da reivindicação e o alerta |
| reivindicação condicional da tarefa | 1.2 e 1.3, sem a ocorrência agendada |

A v1 completa, sem desfazer. **Alternativa rejeitada:** uma `action` provisória só para a v0, que a v1 jogaria fora.

### D2. Reivindicação no banco, só por tarefa
A execução manual precisa ser única entre instâncias, e o mapa em memória não serve para isso. O `UPDATE ... WHERE cSit NOT IN (...)` com contagem de linhas é o mesmo mecanismo condicional da v1 (D3 dela), sem a ocorrência: na v0 só existe a execução manual. O log `102` fica com a situação de origem, que é para onde a tarefa volta.

### D3. Resultado devolvido ao operador, sem repetição
Na v0 não há agendamento para tentar de novo, então o operador precisa ver na hora se a carga veio. A rota responde 200 com o resultado (`SUCESSO`, `NAO_RECEBIDA`, `NAO_IMPLEMENTADA`, `ERRO`), a data-base e o `idCarga`, e o mesmo JSON fica no log da tarefa. "Não recebida" não é erro de execução: o operador tenta de novo mais tarde.

### D4. Parâmetros da v1 já aceitos
`inicioHorario`, `limiteHorario` e `calendarios` são aceitos e guardados, mas sem efeito. Assim as tarefas são cadastradas uma vez, já com o conteúdo de `cadastros-sugeridos.txt` da v1, e passam a ser agendadas quando a v1 chegar, sem recadastro.

### D5. Tarefas não agendadas
As três tarefas ficam `PRONTA`. O motor atual de agendamento não é seguro com duas instâncias (é o que a v1 corrige), então a v0 não agenda nada: o procedimento é não chamar `/agendar` nessas tarefas até a v1.

## Risks / Trade-offs

- **Instância cai no meio da execução manual.** → A tarefa fica `EXECUTANDO` (a expiração é da v1). Recuperação: `POST /api/v1/agendador/tarefas/{id}/cancelar`, e a tarefa pode ser executada de novo.
- **Chamada demora até 120 segundos.** → O operador espera a resposta; o processor responde 503 rápido quando o arquivo não saiu, e a Bloomberg devolve 503 depois de no máximo 90 segundos de espera.
- **Alguém agenda uma tarefa de download na v0.** → O motor antigo dispararia em cada instância. Mitigação: procedimento (D5) e as tarefas cadastradas só pela operação.
- **Mudança no `HttpTaskActionAdapter` quebra tarefas `http` existentes com `url`.** → Tarefas `http` precisam ser recadastradas com `destino` e `caminho`; levantar as existentes antes do deploy.

## Migration Plan

1. Levantar as tarefas `http` cadastradas e preparar o recadastro com `destino` e `caminho`.
2. Configurar `orquestrador.http.destinos`: `conector-b3`, `conector-anbima` e `conector-bloomberg` com a base-URL do `services/processor` e 120 s; `engine` com 30 s, para a v1.
3. Implantar o orquestrador.
4. Cadastrar as três tarefas de `cadastros-sugeridos.txt` da change `orquestrador-curvas`, sem agendar.
5. Executar cada uma manualmente e conferir o resultado e o log.
6. **Rollback:** voltar o deploy. Nenhum schema muda; as tarefas cadastradas ficam `PRONTA`.

## Open Questions

Nenhuma.
