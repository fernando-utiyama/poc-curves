Guia de implementação: [`implementacao.md`](implementacao.md). Cada tarefa diz a classe a mexer e como verificar. Ao lado de cada uma, a tarefa da v1 (`orquestrador-curvas`) que ela antecipa. Testes com mocks e servidor simulado, sem banco real. Nenhum script de banco.

## 1. Base (antecipa a v1)

- [ ] 1.1 Fixar o fuso da JVM em `America/Sao_Paulo` no `main`, antes do Spring, recusando outro fuso; tirar `jackson.time-zone: UTC` e as constantes de fuso (`APP_ZONE`, `UTC_3`), usando o padrão da JVM (v1 1.0); verificar o cenário "Servidor em UTC"
- [ ] 1.2 Corrigir `TarefaJpaMapper` (`action` ↔ `cAcaoOperSist`, `descricao` ↔ `rTrefa`) (v1 1.10); verificar o cenário "Campos lidos como gravados"
- [ ] 1.3 Criar `orquestrador.http.destinos` (base-URL e tempo limite por destino) e o cliente de saída comum: destino + caminho validados, `X-Correlation-Id` em toda chamada, sem `Authorization`, tempo limite do destino (v1 1.7 e 2.2); trocar `url` e `header.*` do `HttpTaskActionAdapter` por `destino` e `caminho`; verificar com servidor simulado destino não cadastrado, caminho com host ou `..`, cabeçalho presente e `Authorization` ausente
- [ ] 1.4 Reivindicação da tarefa no `TaskJpaPersistenceAdapter`/`TaskRepositoryPort`: transação curta com `UPDATE` condicional para `EXECUTANDO` (zero linhas = conflito) e o log `102` com situação de origem, data-base, usuário e instância em JSON; devolução à situação de origem com `UPDATE ... WHERE cSit = 'EXECUTANDO'` (v1 1.2 e 1.3, sem ocorrência); verificar com o repositório simulado a recusa por `EXECUTANDO` (409), `DESABILITADA` e `REMOVIDA` (400), e o SQL enviado

## 2. Execução manual das três tarefas

- [ ] 2.1 Criar `DownloadCargaTaskActionAdapter` (`download-carga-dia`): parâmetros da spec, caminho de download (hoje) ou de reprocessamento (data passada), troca de `{dataBase}` e `{tickers}` (codificado), erro claro sem `tickers`, classificação da resposta (`SUCESSO`, `NAO_RECEBIDA`, `NAO_IMPLEMENTADA`, `ERRO`), sem repetição, janela, calendário nem alerta (v1 2.1, parte manual); verificar os cenários da spec com servidor simulado, inclusive as três fontes e a Bloomberg com e sem `tickers`
- [ ] 2.2 `POST /api/v1/agendador/tarefas/{id}/executar` com `dataBase` opcional (padrão hoje em Brasília, data futura = 400) e `X-Usuario` opcional: reivindica (1.4), roda a `action` fora da transação, grava o log `200` ou `500` com o JSON da spec, volta à situação de origem e responde 200 com o mesmo JSON; tirar o `runningTasks` deste caminho; verificar os cenários "Duas execuções ao mesmo tempo", "Execução volta à situação de origem" e "Execução sem cabeçalho de usuário"

## 3. Fechamento

- [ ] 3.1 Rodar a suíte do orquestrador e `openspec validate orquestrador-v0-disparo-manual --strict`; verificar que tudo passa
- [ ] 3.2 Homologação: configurar os destinos apontando para o processor, cadastrar as três tarefas de `cadastros-sugeridos.txt` sem agendar, executar cada uma para hoje e para uma data passada, com duas instâncias no ar; verificar uma execução por pedido, o resultado na resposta e no log, e as tarefas de volta a `PRONTA`
