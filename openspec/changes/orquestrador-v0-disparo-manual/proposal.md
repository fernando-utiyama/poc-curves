## Why

A change `orquestrador-curvas` (v1) reescreve o motor de agendamento para várias instâncias e cria as três tarefas de download com janela, repetição e alerta. É trabalho grande, e o processor v0 (change `processor-v0`) já expõe as rotas de download das três fontes. Falta um jeito seguro de dispará-las pelo orquestrador enquanto a v1 não fica pronta.

Esta **v0** entrega a base que a v1 usa (fuso de Brasília, campos do cadastro corretos, chamada de saída só para destino cadastrado, cliente sem autenticação com correlação, reivindicação da tarefa no banco) e as `actions` `carga-download-site` e `carga-data-license` no modo **manual**: o operador dispara cada uma das três tarefas, para hoje ou para uma data passada, e vê o resultado. O agendamento automático fica para a v1.

## What Changes

- **Base para a v1, sem retrabalho:** o que a v0 faz é a primeira parte de tarefas da v1 (1.0, 1.7, 1.10, 2.2 e o começo da 2.1 e da reivindicação da 1.2), com o mesmo comportamento.
  - Fuso padrão da JVM em `America/Sao_Paulo`, sem `jackson.time-zone: UTC`.
  - `TarefaJpaMapper` corrigido (`action` ↔ `cAcaoOperSist`, `descricao` ↔ `rTrefa`).
  - Saída só para destino cadastrado (`orquestrador.http.destinos`, base-URL e tempo limite por destino) mais caminho, sem URL livre.
  - Chamadas sem `Authorization`, com `X-Correlation-Id`.
- **Execução manual das três tarefas de download:**
  - `POST /api/v1/agendador/tarefas/{id}/executar` aceita `dataBase` opcional, informada pelo front (via bff). Sem ela, vale a data-base padrão da tarefa: hoje menos o parâmetro `defasagemDiasUteis` (padrão 0), em dias úteis dos `calendarios` da tarefa.
  - A partir da data-base padrão → `caminhoDownload`; antes dela → `caminhoReprocessamento`, ou `caminhoDownload` daquela data se vier `incluirDownload` = `true` (buscar de novo na fonte). A chamada é `POST` com corpo JSON (`dataBase` e, na Bloomberg, `tickers` em lista).
  - Calendários nativos do engine copiados para o orquestrador (sem a sincronização de feriados, que é da v1).
  - Front: executar cada tarefa com data opcional e ver o resultado, ajustando a tela de tarefas que já existe; o bff repassa, sem autenticação.
  - A resposta da function (na v0, o processor) é classificada em sucesso, não recebida ou erro, gravada em `tLogTrefa` com a data-base em JSON e devolvida ao operador.
  - A reivindicação no banco impede duas execuções da mesma tarefa ao mesmo tempo, em qualquer instância (409).
- **Sem agendamento na v0:** as três tarefas ficam `PRONTA`. Janela, repetição a cada 10 minutos, alerta `CARGA_NAO_RECEBIDA`, calendário e recuperação de ocorrências são da v1.
- **Sem infraestrutura nova:** nenhuma tabela, coluna ou tópico.

## Capabilities

### New Capabilities
- `disparo-manual-carga` (orquestrador, bff e front): execução manual das tarefas de carga (`carga-download-site` e `carga-data-license`) com data-base informada pelo front ou padrão com defasagem em dias úteis, calendários nativos, chamada de saída restrita a destino cadastrado, sem autenticação e com correlação, classificação da resposta, log na tarefa e fuso de Brasília.

### Modified Capabilities
<!-- Nenhuma: não há specs arquivadas do orquestrador. -->

## Impact

- **services/orchestrator:** `Application` (fuso), `application.yml` (`orquestrador.http.destinos`, sem `jackson.time-zone`), `TarefaJpaMapper`, `HttpTaskActionAdapter` (destino + caminho), novo `CargaFonteTaskActionAdapter`, cliente de saída comum, classes de calendário copiadas do engine, rota de execução manual com `dataBase` e defasagem, e reivindicação condicional da tarefa no `TaskJpaPersistenceAdapter`.
- **bff e front:** rota de execução com data opcional, sem autenticação, repassada ao orquestrador com `X-Usuario`; na tela de tarefas, executar com data e ver o resultado.
- **Processor:** os destinos `conector-b3`, `conector-anbima` e `conector-bloomberg` apontam para o `services/processor` (change `processor-v0`). Quando as functions existirem, mudam a base-URL de cada destino e os caminhos de cada tarefa (parâmetros, sem redeploy).
- **Banco:** sem mudança de schema; usa `tTrefaAgnda`, `tParmTrefa` e `tLogTrefa` como já existem.
- **v1 (`orquestrador-curvas`):** parte das tarefas já sai feita; a v1 completa a reivindicação por ocorrência, o agendamento, a janela, o alerta e o calendário.
