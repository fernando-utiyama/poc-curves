> **Leitura fechada (economia de tokens):**
> 1. Não leia `proposal.md`, `design.md` nem as specs: cada tarefa abaixo já diz o que fazer. Do [`implementacao.md`](implementacao.md), leia **só a seção citada** na tarefa.
> 2. Abra **só os arquivos listados em "Abrir"** na tarefa. Localize-os pelo **nome do arquivo** (busca por nome, nunca por conteúdo); não liste pastas, não leia outras classes "para entender o padrão" e não rode buscas no repositório.
> 3. Se um arquivo listado não existir com esse nome, não procure alternativas: deixe `// TODO(revisao): <arquivo> não encontrado` no resumo e siga para a próxima tarefa.
> 4. Arquivo novo: crie no mesmo pacote do arquivo de referência citado na tarefa.
> 5. Verifique só com `mvn -q compile` (e o teste citado, quando houver); não rode a suíte.
> 6. Não pare para perguntar.
>
> **Blocos por modelo.** Rode o bloco básico com um **modelo barato** até a PAUSA e pare. Abra uma sessão nova com o **modelo forte** e mande "faça as tarefas 2.x de `openspec/changes/curves-v1-1/tasks.md`".

## 1. Bloco básico (modelo barato)

- [ ] 1.1 [básico] Repasse: criar `EnginePort`, `EngineHttpClient`, `EngineIndisponivelException` e o código `ENGINE_INDISPONIVEL` (503). **Ler:** guia §1.1, §1.2 e o primeiro item de §1.3. **Abrir:** o enum de códigos de erro usado pelo `ApplicationExceptionHandler.java`, `ApplicationExceptionHandler.java`, `messages.properties`, `application.yml`. Arquivos novos: porta no pacote de `CurvaMercdRepositoryPort.java`; cliente em `adapter/out/client/engine/`. Verificar com `mvn -q compile`
- [ ] 1.2 [básico] Repasse: criar `CurvaMercadoAcoesService` (só repassa, sem validação) e `CurvaMercadoAcoesController` com as 4 rotas. **Ler:** guia §1.4 e §1.5. **Abrir:** nada além dos arquivos criados na 1.1; pacotes iguais aos de `CurvaMercadoService.java` e `CurvaMercadoController.java` (não abra esses dois). Verificar com `mvn -q compile`
- [ ] 1.3 [básico] Dono e campos da listagem. **Ler:** guia §2.1 e §2.2 (só "Parâmetros" e "Campos novos no item"). **Abrir:** `CurvaMercdPersistenceAdapter.java`, `CurvaMercadoResponse.java`, o request de criação/alteração da curva (o que o `CurvaMercadoController` recebe no `POST`), `CurvaMercadoController.java`. Copiar `cPprioDado` ↔ `dono`; acrescentar `provedores`, `dono` e `ultimaExecucao` no item e os parâmetros `provedor` e `dono` no controller (a consulta fica para a 2.3). Verificar com `mvn -q compile`
- [ ] 1.4 **PAUSA (troca para o modelo forte):** pronto quando `mvn -q compile` passa; escrever um resumo de até 10 linhas (arquivos criados e alterados, `TODO(revisao)`) e parar.

## 2. Bloco forte (modelo forte)

- [ ] 2.1 [forte] Erros do repasse e validações. **Ler:** guia §1.3 (o resto) e §1.4. **Abrir:** `CurvaMercadoAcoesService.java`, `CurvaMercadoAcoesController.java`, `ApplicationExceptionHandler.java`, `CurvaMercdRepositoryPort.java`. Criar `EngineErroException` e o `@ExceptionHandler` no mesmo formato de corpo que o tratador já usa; no serviço, 404 com a exceção de "não encontrado" que o projeto já usa e os dois 400 (`fonte`/`produto` juntos; interpolação com `du` ou `data`). Escrever `CurvaMercadoAcoesServiceTest` (porta simulada: 404, os dois 400, repasse do status, 503) e fazê-lo passar
- [ ] 2.2 [forte] Consulta da listagem. **Ler:** guia §2.2. **Abrir:** o arquivo que monta a consulta da listagem (o que `CurvaMercadoController` chama para listar), `CurvaPrvdrRepository.java`, `CurvasMercadoPaginadaResponse.java`. Filtros `provedor` e `dono`; provedores da página numa consulta só, na ordem de `cPriorCsumo`; `ultimaExecucao` de `dBaseReft`/`cUsuarCalc`. Verificar com `mvn -q compile`
- [ ] 2.3 [forte] Interpoladores. **Ler:** guia §2.3. **Abrir:** `ValidadorParametros.java`, `ValoresService.java`. Tirar `LogLinear` e a regra "`FlatForward` só com `Discount`", se existirem; se não existirem, anotar e não alterar. Verificar com `mvn -q compile`
- [ ] 2.4 [forte] Resumo final de até 15 linhas: por tarefa, `criado`, `alterado` ou `já estava pronto`, com o arquivo
