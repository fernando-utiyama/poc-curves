> **Como executar com `/opsx-apply`** (o apply já leu `proposal.md`, `design.md` e as specs; não releia):
> 1. O engine v1 e a v1.1 já estão aplicados: abra só os arquivos citados na tarefa; não crie segunda versão do que existe. Caminhos abaixo são de `src/main/java/br/com/poc/` (troque `poc` pelo pacote do repositório real).
> 2. Não pare para perguntar. Se faltar algo, deixe `// TODO(revisao): <dúvida>` e siga.
> 3. Conferência é **por leitura**: não rode a suíte. Cada item termina como **manter**, **alterar** ou **criar**, com `arquivo:linha` de prova. Compile (`mvn -q compile`) só depois de alterar código e rode só o teste citado na tarefa.
> 4. Leitura fechada: abra só os arquivos que a tarefa nomeia (não faça busca por conteúdo no repositório).

## 1. NTN-B: vencimento nominal exato

- [ ] 1.1 [forte] (guia §1) `domain/construcao/NtnbBootstrapAnbima.java:108-124` (a linha 113-116 tem o `withDayOfMonth(15)`): o vencimento nominal passa a ser `dataBase + vVertcCurva`, as datas nominais de evento saem de `V` com `minusMonths(6 * k)` (a partir de `V`, nunca em cadeia), e o pagamento é a data nominal ajustada por `Following`. Pronto quando não há mais `withDayOfMonth` no modelo e `mvn -q compile` passa.
- [ ] 1.2 [forte] (guia §1.2) Teste do modelo (`src/test/java/br/com/poc/domain/construcao/NtnbBootstrapAnbimaTest.java`; se não existir, criar) com os cenários da spec `ntnb-vencimento-exato`: vencimento no dia 15 em dia não útil, vencimento fora do dia 15 e prazo não positivo. Pronto quando os três passam e o teste existente da NTN-B (se houver) continua passando.
- [ ] 1.3 [básico] (guia §1.3) Ler `application/service/ProcessarCargaService.java:93-105` e `domain/interpolacao/PreparacaoVertices.java:27-30` e registrar que nenhum dos dois altera o prazo da NTN-B (**manter**) ou, se alterar, onde.

## 2. SOFR: conjunto esperado de tenores

- [ ] 2.1 [forte] **Antes de codar, confirmar a lista real de tenores** da SOFR (`S0490Z`): o processor tem 20 tickers na reserva e a spec do engine diz 21 (ver `design.md`, pergunta 1). Registrar a lista final no `design.md` e usar a mesma no processor, na spec `sofr-bloomberg-curve-model` e aqui. Sem essa confirmação, a 2.2 fica como `TODO(revisao)`.
- [ ] 2.2 [forte] (guia §2) `domain/construcao/SofrZeroBloomberg.java:48-107, 110-158, 160-173`: constante com o conjunto esperado; depois de descartar o duplicado idêntico, comparar com os tenores lidos e falhar com `INSUMO_INVALIDO` listando os que faltam e os que sobram. Pronto quando `mvn -q compile` passa e a regra aparece uma vez só.
- [ ] 2.3 [forte] (guia §2.2) Teste do modelo (`SofrZeroBloombergTest`; se não existir, criar) com os quatro cenários da spec `sofr-tenores-esperados`: completo, tenor ausente, tenor a mais e duplicado idêntico. Pronto quando passam e o teste atual da SOFR continua passando.

## 3. Conferência de estrutura (spec `convencoes-engine`)

- [ ] 3.1 [básico] (guia §3.1) Imports proibidos: abrir só os 10 services de `application/service/` (`ConstruirCurvaService`, `ConstruirDataService`, `ConsultarFeriadosService`, `ConsultarSituacaoService`, `ConsultarValoresCadastroService`, `ConsultarVerticesService`, `InterpolarCurvaService`, `ProcessarCargaService`, `RegravarInterpoladaService`, `ResolverModelos`) e os arquivos de `domain/curva/`, `domain/cadastro/`, `domain/construcao/`, `domain/interpolacao/` citados no guia; ver só os `import`. Registrar o que importa `adapter.*`, `org.springframework.data`, `jakarta.persistence` ou `com.fasterxml.jackson.databind`.
- [ ] 3.2 [básico] (guia §3.2) Controller chama use case: abrir `CurvaController`, `ConstrucaoDataController`, `CalendarioController` e `CargaController` (em `adapter/in/api/rest/controller/`) e ver o tipo do campo injetado e os `import` de `application`. Registrar o que chama service concreto ou importa `port.out`; criar o use case (`application/port/in/...UseCase`) onde faltar.
- [ ] 3.3 [básico] (guia §3.3) Tipos fortes e um tipo por arquivo: abrir `application/port/` (listar os arquivos de porta) e `domain/curva/ResultadoConstrucao.java`, `PedidoConstrucao.java`, `NotificacaoCarga.java`; registrar `Map<String, Object>`, `Object`, JSON em `String`, record dentro de interface ou record duplicado.
- [ ] 3.4 [básico] (guia §3.4) Nomes e comentários: nos arquivos de 3.1 e 3.2, registrar nome com "canônico", método privado de uma linha usado uma vez sem regra e javadoc com mais de uma linha.
- [ ] 3.5 [básico] (guia §3.5) Jackson e parâmetros: `adapter/in/api/rest/controller/*` e `adapter/out/persistence/jpa/*` (só imports de Jackson), e os controllers com mais de dois `@RequestParam`/corpo (agrupar em record).
- [ ] 3.6 [básico] (guia §3.6) Tempo: `config/TimeZoneConfig.java` e os services de 3.1; registrar `ZoneId` próprio e `now(...)` com argumento.
- [ ] 3.7 [forte] (guia §3.7) Swagger: os 4 controllers de 3.2, `adapter/in/api/rest/config/SwaggerConfiguration.java` e os DTOs de entrada/resposta que eles usam; completar `@Operation`, `@Parameter`, `@ApiResponses`, `produces` e `@Schema` onde faltar, com mensagem de erro inteira.
- [ ] 3.8 [básico] (guia §3.8) Testes: listar `src/test/java/br/com/poc/adapter/out/persistence/` e registrar os que só mockam o repositório e conferem delegação ou `toDomain` (apagar); confirmar que cada service e cada modelo de construção tem teste de regra.
- [ ] 3.9 [forte] Aplicar só os itens **alterar** e **criar** de 3.1 a 3.8, um por vez, compilando depois de cada um; o que depender de infra nova (tópico, tabela, cache) fica como `TODO(revisao)`.

## 4. Fechamento

- [ ] 4.1 [forte] Escrever o resumo no fim deste arquivo: para cada tarefa, **manter**, **alterar** ou **criar**, com `arquivo:linha`, e o que ficou como `TODO(revisao)`.
- [ ] 4.2 [forte] `mvn -q compile`; rodar só `NtnbBootstrapAnbimaTest` e os testes dos arquivos que a tarefa 3.9 alterou.
