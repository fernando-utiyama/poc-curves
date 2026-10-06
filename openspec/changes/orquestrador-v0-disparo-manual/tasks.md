> **Como executar com `/opsx-apply`** (o apply já leu `proposal.md`, `design.md` e as specs; não releia):
> 1. Antes de cada tarefa, leia **só** o cartão dela na seção 0.4 de [`implementacao.md`](implementacao.md) e as seções que o cartão cita. Leia a seção 0 do guia uma vez, no começo.
> 2. Abra só os arquivos que o cartão manda criar ou alterar; não explore o resto do repositório.
> 3. Não pare para perguntar: o guia já decidiu. Se faltar algo, deixe `// TODO(revisao): <dúvida>` no código e siga.
> 4. Cada tarefa termina com o "Pronto quando" do cartão (compilar ou o teste passar); corrija até passar e marque a tarefa.
> 5. Pare na tarefa **1.6** (PAUSA), escreva o resumo e espere a revisão. Depois da revisão, um novo `/opsx-apply` segue com a seção 2.

Guia de implementação: [`implementacao.md`](implementacao.md). Cada tarefa diz a classe a mexer e como verificar. Ao lado de cada uma, a tarefa da v1 (`orquestrador-curvas`) que ela antecipa. Testes poucos e amplos (guia, seção 11), com destino falso e repositório em memória, sem banco real. Nenhum script de banco.

## 1. Base (antecipa a v1)

- [ ] 1.1 Fuso de Brasília (guia, seção 2): `fixarFuso()` no `main`, sem `jackson.time-zone` e sem `APP_ZONE`/`UTC_3` (v1 1.0); escrever o `ApplicationFusoTest`
- [ ] 1.2 Corrigir o `TarefaJpaMapper` (seção 3) (v1 1.10); escrever o `TarefaJpaMapperTest`
- [ ] 1.3 `DestinosProperties`, `ChamadaSaidaClient`/`RestClientChamadaSaidaClient`, `ExecucaoErrorCode`, `ConflictException` com o método no handler, e o `HttpTaskActionAdapter` com `destino` e `caminho` (seção 4) (v1 1.7 e 2.2); escrever o `ChamadaSaidaClientTest` e ajustar os testes existentes da action `http`
- [ ] 1.4 `reivindicar`, `devolver` e `registrarLog` no `TaskRepositoryPort`/`TaskJpaPersistenceAdapter` (seção 5) (v1 1.2 e 1.3, sem ocorrência); verificar com `mvn -q compile`
- [ ] 1.5 `Calendario`, `Brazil`, `UnitedStates` e `Calendarios` em `application/model/calendario/` (seção 6, código pronto) (v1 1.11, parte); escrever o `CalendariosTest` com os vetores da seção 6
- [ ] 1.6 **PAUSA:** com 1.1 a 1.5 prontas, rodar `mvn compile` e `mvn test`; escrever um resumo curto (arquivos criados, arquivos alterados, testes e resultado, `TODO(revisao)` deixados) e parar até a revisão; não começar a seção 2 antes disso

## 2. Execução manual das três tarefas

- [ ] 2.1 `ExecucaoManualActionPort`, `ResultadoExecucao`, `TipoResultado` e `CargaFonteTaskActionAdapter` (seção 8) (v1 2.1, parte manual); escrever o `CargaFonteTaskActionAdapterTest`
- [ ] 2.2 Nova assinatura de `SchedulerUseCase.executeTask`, o `SchedulerService.executeTask` reescrito, `SchedulerAPI`/`SchedulerController.executar` e `ExecucaoManualResponseDto` (seção 9); escrever o `ExecucaoManualServiceTest` e o `SchedulerExecutarRotaTest`
- [ ] 2.3 bff: rota de execução, sem autenticação, com `dataBase` e `incluirDownload` opcionais, repassada ao orquestrador com `X-Usuario` e `X-Correlation-Id`, sem token, devolvendo a resposta sem alteração; verificar o repasse com e sem data e sem `Authorization`
- [ ] 2.4 Front (change `fed-tarefas-orquestrador`; a tela já existe, só ajustar): na lista de tarefas de download, "Executar" com campo de data opcional (`dd/mm/aaaa`, vazio = data-base padrão) e a opção "Baixar de novo da fonte" quando há data e o resultado em pt-BR (situação, data-base usada, identificador da carga, detalhe do erro); verificar no navegador sem data, com data passada e com o processor respondendo 503

## 3. Fechamento

- [ ] 3.1 Rodar a suíte do orquestrador e `openspec validate orquestrador-v0-disparo-manual --strict`; verificar que tudo passa
