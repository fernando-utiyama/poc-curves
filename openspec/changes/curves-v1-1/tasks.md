> **Como executar com `/opsx-apply`** (o apply já leu `proposal.md`, `design.md` e as specs; não releia):
> 1. Trabalhe tarefa por tarefa; cada tarefa cita a seção de [`implementacao.md`](implementacao.md); abra só os arquivos da tabela §0 do guia.
> 2. A v1 já está aplicada: procure pela funcionalidade antes de criar; não crie segunda versão do que existe.
> 3. Não pare para perguntar. Se faltar algo, deixe `// TODO(revisao): <dúvida>` e siga.
> 4. Verifique com `mvn -q compile` depois de alterar código e rode só os testes citados na tarefa, não a suíte.
>
> **Blocos por modelo.** Rode o bloco básico com um **modelo barato** até a PAUSA e pare. Abra uma sessão nova com o **modelo forte** e rode `/opsx-apply` de novo: ele segue da primeira tarefa desmarcada, começando por conferir o diff do básico.

## 1. Bloco básico (modelo barato)

- [ ] 1.1 [básico] (guia §1.1) `EngineRepassePort`, `RespostaEngine`, `EngineHttpClient`, `EngineIndisponivelException` e `ENGINE_INDISPONIVEL` (503) no enum, no tratador e em `messages.properties`; `curves.engine.url` no `application.yml`; verificar com `mvn -q compile`
- [ ] 1.2 [básico] (guia §1.2) `CurvaMercadoAcoesController` com as quatro rotas da spec `repasse-engine`, chamando um `CurvaMercadoAcoesService` que só repassa ao `EngineRepassePort`; verificar com `mvn -q compile`
- [ ] 1.3 [básico] (guia §2) `dono` ↔ `cPprioDado` no adaptador, no request e no response da curva; campos `provedores`, `dono` e `ultimaExecucao` no item da listagem e os parâmetros `provedor` e `dono` no controller (a consulta que os preenche fica para a 2.3); verificar com `mvn -q compile`
- [ ] 1.4 **PAUSA (troca para o modelo forte):** pronto quando `mvn -q compile` passa; escrever o resumo (arquivos, `TODO(revisao)`) e parar.

## 2. Bloco forte (modelo forte)

- [ ] 2.1 [forte] Conferir o diff do bloco básico contra as specs e o guia; corrigir só o que diverge
- [ ] 2.2 [forte] (guia §1.2) No `CurvaMercadoAcoesService`, o 404 e os dois 400 antes de chamar o engine; no controller, o 4xx do engine no formato de erro da curves; escrever `CurvaMercadoAcoesServiceTest` e fazê-lo passar
- [ ] 2.3 [forte] (guia §2) Filtros `provedor` e `dono` na consulta da listagem; provedores da página numa consulta só, na ordem de prioridade; `ultimaExecucao` de `dBaseReft`/`cUsuarCalc`; verificar os dois cenários da spec `listagem-curvas-v1-1` com o repositório simulado
- [ ] 2.4 [forte] Escrever o resumo final: o que foi criado, alterado ou já estava pronto em cada tarefa
