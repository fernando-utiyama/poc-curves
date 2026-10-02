## Context

A motivação está no proposal e o comportamento na spec. Estado atual do `services/orchestrator` transcrito:

- `HttpTaskActionAdapter` chama qualquer `url` vinda do parâmetro da tarefa, com cabeçalhos livres (`header.*`): é a falha de SSRF que a v1 corrige na tarefa 1.7.
- A execução manual (`SchedulerService.executeTask`) usa um mapa em memória (`runningTasks`) para saber o que está rodando: não vale entre instâncias.
- `jackson.time-zone: UTC` no `application.yml` e constantes de fuso espalhadas; `TarefaJpaMapper` troca `action` e `descricao`.
- Nenhuma `action` de download existe; o processor v0 expõe as rotas de download e reprocessamento das três fontes no contrato das functions.

## Goals / Non-Goals

**Goals:**
- Disparar manualmente as três tarefas de download (hoje ou data passada) e ver o resultado.
- Fazer agora, do jeito da v1, o que a v1 também precisa: fuso, mapper, destino cadastrado, cliente de saída, reivindicação no banco, `action` de download.

**Non-Goals (ficam para a v1, `orquestrador-curvas`):**
- Agendamento: ocorrências alinhadas, reivindicação por ocorrência, reconciliação, recuperação, expiração de execução.
- Janela (`inicioHorario`, `limiteHorario`), repetição a cada 10 minutos, alerta `CARGA_NAO_RECEBIDA`, `GET /api/v1/alertas`.
- Sincronização de feriados decretados com o engine (os calendários nativos entram na v0, para a defasagem).
- Remoção de `TarefaService`, webhook de notificação e `scheduler.*`.

## Decisions

### D1. Cada peça da v0 é a primeira parte de uma tarefa da v1
| v0 | v1 |
|---|---|
| fuso de Brasília | 1.0 inteira |
| mapper | 1.10 inteira |
| destino + caminho, cliente sem autenticação com correlação | 1.7 e 2.2 inteiras |
| `CargaFonteTaskActionAdapter`, modo manual | 2.1, sem a janela, o encerramento antes da reivindicação e o alerta |
| reivindicação condicional da tarefa | 1.2 e 1.3, sem a ocorrência agendada |

A v1 completa, sem desfazer. **Alternativa rejeitada:** uma `action` provisória só para a v0, que a v1 jogaria fora.

### D2. Reivindicação no banco, só por tarefa
A execução manual precisa ser única entre instâncias, e o mapa em memória não serve para isso. O `UPDATE ... WHERE cSit = <situação lida>` com contagem de linhas (zero = outra execução passou na frente) é o mesmo mecanismo condicional da v1 (D3 dela), sem a ocorrência: na v0 só existe a execução manual. O log `102` fica com a situação de origem, que é para onde a tarefa volta.

### D3. Resultado devolvido ao operador, sem repetição
Na v0 não há agendamento para tentar de novo, então o operador precisa ver na hora se a carga veio. A rota responde 200 com o resultado (`SUCESSO`, `NAO_RECEBIDA`, `NAO_IMPLEMENTADA`, `ERRO`), a data-base e o `idCarga`, e o mesmo JSON fica no log da tarefa. "Não recebida" não é erro de execução: o operador tenta de novo mais tarde.

### D4. Parâmetros da v1 já aceitos
`inicioHorario` e `limiteHorario` são aceitos e guardados, mas sem efeito; `calendarios` já é usado na v0, para contar a defasagem. Assim as tarefas são cadastradas uma vez, já com o conteúdo de `cadastros-sugeridos.txt` da v1, e passam a ser agendadas quando a v1 chegar, sem recadastro.

### D5. Tarefas não agendadas
As três tarefas ficam `PRONTA`. O motor atual de agendamento não é seguro com duas instâncias (é o que a v1 corrige), então a v0 não agenda nada: o procedimento é não chamar `/agendar` nessas tarefas até a v1.

### D6. Data-base padrão com defasagem em dias úteis
Nem toda fonte publica o dado do próprio dia no horário da carga. O parâmetro `defasagemDiasUteis` da tarefa diz quantos dias úteis a data-base padrão fica antes de hoje, contados nos `calendarios` da tarefa (dia útil em todos). Sem data informada, vale essa data-base padrão. A data-base padrão também separa download de reprocessamento: a partir dela, é a carga "do dia" (download); antes dela, é reprocessamento. Assim, uma tarefa com defasagem 1 não cai no reprocessamento todo dia. **Alternativa rejeitada:** defasagem em dias corridos, que erra na segunda-feira e depois de feriado.

### D7. Calendários nativos já na v0
Para contar dias úteis, a v0 copia do engine as classes de calendário (parte da tarefa 1.11 da v1), sem a sincronização diária de feriados decretados, que fica para a v1. Um feriado decretado e não conhecido pelo calendário nativo pode deslocar a data-base padrão em um dia; o operador informa a data pelo front nesse caso.

### D8. Data pelo front, passando pelo bff
O front fala só com o bff, que autentica e repassa ao orquestrador com `X-Usuario`. Campo vazio significa "data-base padrão", calculada no orquestrador, que é quem conhece a defasagem e os calendários da tarefa; a resposta devolve a data usada para a tela mostrar.

### D9. Reprocessar com ou sem novo download
Uma data anterior à padrão é reprocessamento. Por padrão, o destino relê o original guardado no Blob, sem ir à fonte (`caminhoReprocessamento`). Quando o original está errado ou não existe, o operador marca `incluirDownload`, e o orquestrador chama o `caminhoDownload` com aquela data: o destino busca na fonte, guarda o novo original e grava. A decisão fica no orquestrador, que só escolhe o caminho; o contrato das rotas das functions e do processor não muda (o download já aceita data passada). **Alternativa rejeitada:** um parâmetro novo na rota de reprocessamento do destino, que mudaria o contrato das functions.

## Risks / Trade-offs

- **Instância cai no meio da execução manual.** → A tarefa fica `EXECUTANDO` (a expiração é da v1). Recuperação: `POST /api/v1/agendador/tarefas/{id}/cancelar`, e a tarefa pode ser executada de novo.
- **Chamada demora até 120 segundos.** → O operador espera a resposta; o processor responde 503 rápido quando o arquivo não saiu, e a Bloomberg devolve 503 depois de no máximo 90 segundos de espera.
- **Alguém agenda uma tarefa de download na v0.** → O motor antigo dispararia em cada instância. Mitigação: procedimento (D5) e as tarefas cadastradas só pela operação.
- **Mudança no `HttpTaskActionAdapter` quebra tarefas `http` existentes com `url`.** → Tarefas `http` precisam ser recadastradas com `destino` e `caminho`; levantar as existentes antes do deploy.

## Migration Plan

1. Levantar as tarefas `http` cadastradas e preparar o recadastro com `destino` e `caminho`.
2. Configurar `orquestrador.http.destinos`: `conector-b3`, `conector-anbima` e `conector-bloomberg` com a base-URL do `services/processor` e 120 s; `engine` com 30 s, para a v1.
3. Implantar o orquestrador.
4. Cadastrar as três tarefas de `cadastros-sugeridos.txt` da change `orquestrador-curvas`, sem agendar, com a `defasagemDiasUteis` confirmada para cada fonte.
5. Implantar o bff e o front com a execução por data.
6. Executar cada uma pelo front, com e sem data, e conferir o resultado e o log.
7. **Rollback:** voltar o deploy. Nenhum schema muda; as tarefas cadastradas ficam `PRONTA`.

## Open Questions

- [A CONFIRMAR] `defasagemDiasUteis` de cada fonte (sugestão: 0 nas três; a Bloomberg pode precisar de 1 se a SOFR do dia não sair até o fim da janela).
