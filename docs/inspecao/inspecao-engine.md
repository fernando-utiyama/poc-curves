# Inspeção do engine (v1) — somente leitura

> Transcrito das fotos do relatório gerado pelo Copilot no repositório real (2026-10-06, branch `feature/ACECSPB-33149`, PR #3), conforme `openspec/changes/engine-construcao-curvas/verificacao.md` e o guia `implementacao.md`. O Copilot não alterou código nem `tasks.md`; não compilou nem rodou testes. Pacote `bradesco` trocado por `poc` nos caminhos.

Status: `pronto` = implementação visível no código atende ao item; `diverge` = há implementação, mas existe divergência demonstrável; `falta` = não encontrei a funcionalidade no código; `não sei` = o código disponível não permite concluir.

## A. Funcionalidades

**1.1 Contagem de tempo e cotação**
- **Status:** `pronto`
- **Prova:** `src/main/java/br/com/poc/domain/quantlib/DayCounter.java:9-48`; `InterestRate.java:13-56`; `Periodos.java:6-25`; `application/service/ConstruirCurvaService.java:248-260`
- **O que diverge ou falta:** Usa `BigDecimal`, `(base,data]` para dias corridos, 30/360 Bond Basis, conversão taxa/fator, recusa os dois regimes mistos e parseia `nD/nW/nM/nY`. Arredondamento da curva usa `setScale` com modo cadastrado; não há tipo próprio de arredondamento.

**2.1 Calendários**
- **Status:** `não sei`
- **Prova:** `domain/calendario/Calendario.java:20-77`; `Brazil.java:8-62`; `UnitedStates.java:11-47`; `src/test/java/br/com/poc/domain/construcao/VetoresReaisTest.java:117-128`
- **O que diverge ou falta:** As regras e as sete convenções de ajuste estão implementadas; a conferência de DU em massa usa `2026-09-02`, não `2026-09-14`, portanto o vetor exigido de 278 vértices não está comprovado pelo teste existente.

**3.1 Interpoladores, bases e extrapolação**
- **Status:** `diverge`
- **Prova:** `domain/interpolacao/FlatForward.java:22-42`; `InterpoladorPorSegmento.java:10-36`; `BaseInterpolacao.java:9-30`; `application/service/ResolverModelos.java:28-33`; `domain/cadastro/ValidadorCadastro.java:204-217`
- **O que diverge ou falta:** Não há classe/registro `LogLinear`; o registry não a registra. `FlatForward` não é restringido à base `Discount` (validação atual restringe apenas as políticas FlatForward a interpoladores `Linear`/`FlatForward`).

**3.2 Domínio, classificação e fatores**
- **Status:** `pronto`
- **Prova:** `domain/interpolacao/CurvaInterpolada.java:39-67, 78-171`; `Classificacao.java:3-9`; `ValorNoPrazo.java:6-15`
- **O que diverge ou falta:** Calcula início `advance(base,1)`, fim `max(último,horizonte)`, classificação, valor em nó, curva de um vértice e fatores somente para unidade `TAXA`, calculados do valor arredondado a 16 casas. `FORA_DO_DOMINIO` também está no enum, embora fora do domínio lance exceção.

**3.3 Dias úteis publicados**
- **Status:** `pronto`
- **Prova:** `domain/interpolacao/EixoDiasUteis.java:15-54`; `application/service/ConstruirCurvaService.java:234-245`
- **O que diverge ou falta:** O eixo usa DU publicado no vértice e ancora DU intermediário no ponto anterior, limitado pelo seguinte; a construção gera aviso quando DU publicado diverge do calendário ou a data não é útil.

**3.4 Vértices no mesmo prazo**
- **Status:** `diverge`
- **Prova:** `domain/interpolacao/PreparacaoVertices.java:21-56`; `application/service/ConstruirCurvaService.java:265-269`; `domain/quantlib/DayCounter.java:17-21`
- **O que diverge ou falta:** Descarta prazo não positivo e repetido e gera avisos, mas quando `diasUteis` é nulo a preparação usa DU `0` (`PreparacaoVertices.java:27-30`). Para eixo `Business252`, pode descartar ponto sem DU publicado/informado em vez de resolver pelo calendário. Também não encontrei chamada de log específica para os avisos de descarte.

**4.1 Leitura e validação do cadastro**
- **Status:** `diverge`
- **Prova:** `adapter/out/persistence/jpa/CurvaMercadoJpaAdapter.java:129-175`; `domain/cadastro/ValidadorCadastro.java:45-146, 148-238, 292-307, 340-358`; `domain/cadastro/TabelaParametros.java:8-54`; `adapter/out/persistence/jpa/entity/CurvaMercdEntity.java:51-56`
- **O que diverge ou falta:** Lê `cModDado` como JSON e agrega erros. Aceita `NoFrequency`, `Once` e `OtherFrequency` embora a spec os recuse (`TabelaParametros.java:15-17`; `ValidadorCadastro.java:188-201`); certos tipos errados também podem falhar em casts antes de `CADASTRO_INVALIDO` (`ValidadorCadastro.java:188, 204`).

**5.1 B3 pronta**
- **Status:** `diverge`
- **Prova:** `domain/construcao/TaxaSwapB3.java:13-27, 31-90`; `application/service/ResolverModelos.java:23-26`; `src/test/java/br/com/poc/domain/construcao/TaxaSwapB3Test.java:41, 76-82, 92-123`
- **O que diverge ou falta:** Funcionalidade de valores prontos B3 existe, mas o nome é `TaxaSwapB3`/`TAXA_SWAP_B3`, não `ProntaTsB3`/`PRONTA_TS_B3`. A massa de construção do teste usa `2026-09-02`, não a data-base do aceite.

**5.2 NTN-B por bootstrap**
- **Status:** `diverge`
- **Prova:** `domain/construcao/NtnbBootstrapAnbima.java:71-84, 108-124, 129-150, 176-219`
- **O que diverge ou falta:** O bootstrap por bisseção existe, mas transforma a data `base + prazo` no dia 15 (`a.withDayOfMonth(15)`, linhas 113-116), em vez de usar o vencimento nominal exatamente em `base + vVertcCurva` e ajustar esse dia por Following. Aceita só `Linear`/`FlatForward`, não `LogLinear` (`73-76`).

**5.3 SOFR Bloomberg**
- **Status:** `diverge`
- **Prova:** `domain/construcao/SofrZeroBloomberg.java:48-107, 110-158, 160-173`
- **O que diverge ou falta:** Parseia tenores, valida membro, resolve datas e trata duplicatas idênticas/divergentes. Não valida que a fonte trouxe exatamente os 21 tenores esperados; agrupa apenas os tenores recebidos.

**6.1 Construir e gravar**
- **Status:** `diverge`
- **Prova:** `application/service/ConstruirCurvaService.java:59-75, 117-159, 152-195, 229-271`; `adapter/out/persistence/jpa/DadoCurvaJpaAdapter.java:31-66, 81-90`; `domain/interpolacao/InterpolacaoDadoCurva.java:14-34`
- **O que diverge ou falta:** Pipeline apaga e grava pontos e grade, calcula situação/hash e atualiza resumo. Diverge porque grava `duPub` no ponto, não o DU calculado (`ConstruirCurvaService.java:234-262`); para SOFR ou entrada sem DU, `cDiaUtil` fica nulo. Sem `X-Usuario`, grava `SISTEMA` e não nulo (`159`). O código não demonstra os números reais 278/12.390 nem persistência íntegra sob banco real.

**6.2 Trava e leitura consistente**
- **Status:** `não sei`
- **Prova:** `adapter/out/persistence/jpa/CurvaMercadoJpaAdapter.java:94-108`; `application/service/ConstruirCurvaService.java:59-60`; `src/main/resources/application.yml:29-48`
- **O que diverge ou falta:** A trava usa `UPDLOCK, ROWLOCK`, timeout do comando e traduz timeout em `CONSTRUCAO_EM_ANDAMENTO`. Não há configuração explícita de isolamento `READ_COMMITTED`; o isolamento efetivo do banco operacional não é comprovado pelo código inspecionado.

**7.1 Consultar e interpolar**
- **Status:** `diverge`
- **Prova:** `application/service/ConsultarVerticesService.java:65-78, 133-149, 160-169`; `InterpolarCurvaService.java:26-37, 49-103`; `adapter/in/api/rest/controller/CurvaController.java:98-118, 131-185`
- **O que diverge ou falta:** Reconsulta vértices e interpola prazos, limita a 5.000 e recusa query params desconhecidos. Diverge do enunciado estrito "sem usar `tDadoCurva`": a consulta lê a interpolada para comparar desatualização (`ConsultarVerticesService.java:136-149`). A resposta chama a lista `pontos`, não `vertices`.

**7.2 Regravar a interpolada**
- **Status:** `diverge`
- **Prova:** `application/service/RegravarInterpoladaService.java:55-65, 77-97`; `adapter/in/api/rest/controller/CurvaController.java:121-129`
- **O que diverge ou falta:** Regrava só `tDadoCurva`. No caso sem vértices, apaga a interpolada e lança exceção dentro de método `@Transactional` (`63-65`); a exceção provoca rollback da transação, portanto a exclusão não é garantida. Usuário do controller é hardcoded.

**8.1 Carga do processor**
- **Status:** `diverge`
- **Prova:** `application/service/ProcessarCargaService.java:37-64, 67-105`; `domain/curva/PedidoConstrucao.java:6-16`; `application/service/ConstruirCurvaService.java:229-245`
- **O que diverge ou falta:** Webhook agrupa curvas por origem principal e dispara construção, mas a quantidade `verticesPorCodigo` é apenas passada em `linhasAvisadas`; não há comparação da quantidade lida com a avisada nem emissão de `INSUMO_INCOMPLETO` no pipeline mostrado.

**8.2 Construção pela API e da data inteira**
- **Status:** `diverge`
- **Prova:** `adapter/in/api/rest/controller/ConstrucaoDataController.java:23-32`; `application/service/ConstruirDataService.java:50-124`; `domain/curva/DisparoConstrucao.java:3-7`
- **O que diverge ou falta:** Rota e paralelismo existem; insumo ausente é convertido em `SemInsumo`. O disparo usa `DisparoConstrucao.CONSTRUCAO`, não `DATA_INTEIRA`; não há construção de derivadas depois dos provedores.

**8.3 Situação da data**
- **Status:** `diverge`
- **Prova:** `application/service/ConsultarSituacaoService.java:27-35, 52-99`; `adapter/in/api/rest/controller/CurvaController.java:55-70`
- **O que diverge ou falta:** Calcula cadastro/presença/hash dos pontos em paralelo, mas o DTO não confere insumo, execução do modelo, igualdade com fonte nem estado de interpolada; curva com falha/timeout vira item genérico sem indicação específica de timeout (`93-97`).

**9.1 Rotas, contrato e erros**
- **Status:** `diverge`
- **Prova:** `adapter/in/api/rest/controller/CurvaController.java:73-129`; `CurvaPorNomeController.java:32-59`; `domain/curva/ResultadoConstrucao.java:6-60`; `application/service/ConsultarVerticesService.java:28-51`; `ApplicationExceptionHandler.java:36-37, 85-112`
- **O que diverge ou falta:** Rotas centrais existem, mas há divergências de campos/contrato listadas na seção C: usuário hardcoded, resposta consulta `pontos`, resultado sem campo `situacao`, erro usa `code`/Problem Details e não `codigo`/`mensagem`. Não há rota separada `/pontos`.

**9.2 Valores aceitos e calendário**
- **Status:** `diverge`
- **Prova:** `application/service/ConsultarValoresCadastroService.java:46-97`; `application/service/ConsultarFeriadosService.java:33-64`; `adapter/in/api/rest/controller/CalendarioController.java:22-58`; `ResolverModelos.java:28-35`
- **O que diverge ou falta:** Serviços/rotas existem. O catálogo inclui frequências recusadas pelo validador e não inclui `LogLinear`; o calendário aceita intervalo/ano e gera feriados pelos calendários nativos.

**9.3 Fuso e logs**
- **Status:** `diverge`
- **Prova:** `Application.java:29-31`; `config/TimeZoneConfig.java:9-19`; `application/service/ConstruirCurvaService.java:172-187`; `adapter/out/log/EventosLogAdapter.java:23-40`
- **O que diverge ou falta:** Define e confere `America/Sao_Paulo`; eventos são serializados em JSON com correlação. O pipeline mostrado emite `CONSTRUCAO_CONCLUIDA` e `CURVA_GRAVADA`, mas não há emissão no caminho de falha nem de `INSUMO_DESCARTADO`; faltam os eventos/campos completos exigidos.

**10.1 Vetores reais como teste**
- **Status:** `diverge`
- **Prova:** `src/test/java/br/com/poc/domain/construcao/VetoresReaisTest.java:81-114, 117-128, 152-168`; `src/test/java/br/com/poc/domain/construcao/FixturesB3.java:15-24`; `domain/curva/HashPontos.java:17-26`
- **O que diverge ou falta:** A função de hash existe, mas as cinco hashes da spec aparecem no teste apenas como strings verificadas com `isNotEmpty()` (`163-167`); a interpolação B3 usa dois pontos artificiais (`91-113`) e o teste de DU usa base `2026-09-02`. O teste de 12.390 verifica apenas a diferença entre datas (`152-158`), não as linhas/valores da grade persistida.

## B. Rotas expostas (`/api/v1`)

| Rota esperada | Existe? | Método e caminho reais | Classe:linha |
|---|---|---|---|
| `POST /cargas` | Sim | `POST /api/v1/cargas` | `adapter/in/api/rest/controller/CargaController.java:12, 21-23` |
| `POST /construcoes/{dataBase}` | Sim | `POST /api/v1/construcoes/{dataBase}` | `ConstrucaoDataController.java:14, 23-31` |
| `GET /curvas?nome=` | Sim | `GET /api/v1/curvas?nome=` | `CurvaController.java:20, 46-53` |
| `GET /curvas/situacao?dataBase=` | Sim | `GET /api/v1/curvas/situacao?dataBase=` | `CurvaController.java:55-70` |
| `GET /valores-cadastro` | Sim | `GET /api/v1/valores-cadastro` | `ValoresCadastroController.java:14, 23-30` |
| `POST /curvas/{codigo}/{dataBase}/construcao?forcarRecalculo=&fonte=&produto=` | Sim | `POST /api/v1/curvas/{codigo}/{dataBase}/construcao` | `CurvaController.java:73-95` |
| `GET /curvas/{codigo}/{dataBase}` | Sim | `GET /api/v1/curvas/{codigo}/{dataBase}` | `CurvaController.java:98-106` |
| `GET /curvas/{codigo}/{dataBase}/interpolacao?du=&data=` | Sim | `GET /api/v1/curvas/{codigo}/{dataBase}/interpolacao` | `CurvaController.java:109-118` |
| `POST /curvas/{codigo}/{dataBase}/interpolada` | Sim | `POST /api/v1/curvas/{codigo}/{dataBase}/interpolada` | `CurvaController.java:121-129` |
| `GET /curvas/por-nome/{dataBase}` e `.../interpolacao` | Sim | `GET /api/v1/curvas/por-nome/{dataBase}?nome=` e `GET /api/v1/curvas/por-nome/{dataBase}/interpolacao?nome=&du=&data=` | `CurvaPorNomeController.java:18, 32-59` |
| `GET /calendarios/{nome}` | Sim | `GET /api/v1/calendarios/{nome}` | `CalendarioController.java:13, 22-58` |
| `GET /curvas/{codigo}/{dataBase}/pontos` (nova) | Não | Não há mapping `/pontos`; a listagem está em `GET /api/v1/curvas/{codigo}/{dataBase}` | mappings de `CurvaController.java:46, 55, 73, 98, 109, 121` |
| Outras rotas | Sim | `GET /health`; forwarding `GET /swagger-ui/` → `/swagger-ui/index.html`; documentação Springdoc agrupada em `/api/v1/**` | `src/main/resources/application.yml:180-208`; `adapter/in/api/rest/config/SwaggerConfiguration.java:20-24, 41-45, 58-65` |

## C. Contrato com quem chama o engine

**Corpo de `POST /cargas` (processor)**
- **Esperado:** `idCarga`, `fonte`, `produto`, `dataBase`, `verticesPorCodigo`
- **No código (prova):** Record com os cinco campos: `domain/curva/NotificacaoCarga.java:6-12`; controller recebe esse tipo: `CargaController.java:21-23`
- **Bate?:** Sim

**Cabeçalho do usuário**
- **Esperado:** `X-Usuario`, opcional, nulo sem ele
- **No código (prova):** Não há `@RequestHeader`/`X-Usuario` no código; construção de curva passa literal `"OPERADOR"`: `CurvaController.java:73-95`; regravação passa literal: `121-129`
- **Bate?:** Não: usuário hardcoded; API não lê o cabeçalho nem passa nulo quando ausente

**Cabeçalho de correlação**
- **Esperado:** Recebe ou gera `X-Correlation-Id`, devolve em toda resposta
- **No código (prova):** Lê/gera UUID e define header antes de seguir a cadeia: `adapter/in/api/rest/filter/FiltroCorrelacao.java:20-39`
- **Bate?:** Sim

**Autenticação**
- **Esperado:** Nenhuma
- **No código (prova):** Busca no `pom.xml` e em `src/main/java` não encontrou starter/configuração Spring Security, `SecurityFilterChain`, JWT ou `@RequestHeader`; controllers expõem mappings sem anotações de autorização, ex. `CurvaController.java:19-21`
- **Bate?:** Aparentemente sim; não há autenticação do engine no código inspecionado

**Resposta da construção**
- **Esperado:** `codigo`, `nome`, `dataBase`, `situacao`, `quantidadePontos`, `hashPontos`, `duracaoMs`, `avisos`
- **No código (prova):** `Construida`/`Reconstruida` expõem `pontos`, não `quantidadePontos`; `Existente` tem outro conjunto de campos e nenhum discriminador `situacao`: `domain/curva/ResultadoConstrucao.java:16-60`; resposta direta: `CurvaController.java:84-95`
- **Bate?:** Não: campos e variações do resultado diferem do contrato esperado

**Resposta da consulta**
- **Esperado:** Identificação, hash, avisos e vértices com fatores
- **No código (prova):** Record chama coleção `pontos`, e fatores `fatorAcum`/`fatorDia`; inclui campos recalculados: `application/service/ConsultarVerticesService.java:28-51`
- **Bate?:** Não: nome real da lista é `pontos`; nomes de fatores e payload têm campos adicionais/diferentes

**Resposta da interpolação**
- **Esperado:** `prazos[]` com `pedido`, `data`, `du`, `dc`, `valor`, `classificacao`, fatores
- **No código (prova):** Wrapper chama a lista `prazos`: `application/service/InterpolarCurvaService.java:28-37`; `ValorNoPrazo` contém `data, du, dc, x, valor, classificacao, fatorAcum, fatorDia`, não `pedido`: `domain/interpolacao/ValorNoPrazo.java:6-15`
- **Bate?:** Parcial: lista e a maioria dos valores existem, mas faltam `pedido` e os nomes longos esperados dos fatores; inclui `x`

**Formato de erro**
- **Esperado:** campo de código, mensagem, `detalhes`, `correlationId`
- **No código (prova):** RFC Problem Details; propriedade do código chama-se `code`; `correlationId` e `detalhes` são adicionados condicionalmente; mensagem padrão segue os campos RFC (`title`/`detail`): `adapter/in/api/rest/exception/handler/ApplicationExceptionHandler.java:36-37, 85-112`
- **Bate?:** Não: nome real `code`, mensagem em `detail` (não `mensagem`) e `detalhes` só aparece se não vazio

**Decimais**
- **Esperado:** string, sem expoente
- **No código (prova):** Serializador registrado em `JsonConfiguration.java:39-46` chama `BigDecimal.toPlainString()` (`41-43`)
- **Bate?:** Sim

**`avisos`**
- **Esperado:** `{ codigo, mensagem, detalhes }`, lista vazia quando não há
- **No código (prova):** Record corresponde ao formato: `domain/curva/AvisoCurva.java:5`; listas são inicializadas no pipeline/consulta: `application/service/ConstruirCurvaService.java:88, 226-227`; `ConsultarVerticesService.java:81-82`
- **Bate?:** Parcial: forma do item bate; resultados `Ignorada`, `SemInsumo` e `Falhou` não têm campo `avisos`: `domain/curva/ResultadoConstrucao.java:46-60`

## D. Banco compartilhado com a curves e o processor

**Lê `cModDado` de `tConfgCurva` como JSON**
- **Esperado:** chaves da tabela da spec `curve-build-pipeline`
- **No código (prova):** Entidade mapeia `cModDado`: `adapter/out/persistence/jpa/entity/ConfgCurvaEntity.java:15-20, 36-37`; lê e desserializa para `Map`: `adapter/out/persistence/jpa/CurvaMercadoJpaAdapter.java:140-150`; valida contra `TabelaParametros`: `domain/cadastro/ValidadorCadastro.java:45-146`
- **Bate?:** Sim para JSON e chaves; ressalva: não recusa `NoFrequency`, `Once` e `OtherFrequency` (`ValidadorCadastro.java:188-201`)

**Origem principal**
- **Esperado:** menor `cPriorCsumo` em `tCurvaPrvdr`
- **No código (prova):** Ordena por prioridade ascendente (e ID como desempate) e preserva essa ordem: `adapter/out/persistence/jpa/repository/CurvaPrvdrJpaRepository.java:10-13`; `CurvaMercadoJpaAdapter.java:117-120`; principal é `origens.getFirst()`: `domain/curva/CurvaMercado.java:27-29`
- **Bate?:** Sim

**Curva ativa**
- **Esperado:** `trim(cSitReg)` = `ATIVO`
- **No código (prova):** Getter remove espaços finais: `adapter/out/persistence/jpa/entity/CurvaMercdEntity.java:51-56`; validador aplica `strip()` e igualdade exata: `domain/cadastro/ValidadorCadastro.java:148-152, 340-348`
- **Bate?:** Sim

**Escreve em `tCurvaMercd`**
- **Esperado:** só `dBaseReft` e `cUsuarCalc`
- **No código (prova):** UPDATE altera somente essas colunas: `adapter/out/persistence/jpa/repository/CurvaMercdJpaRepository.java:20-22`
- **Bate?:** Sim quanto às colunas; diverge no usuário ausente, que é substituído por `SISTEMA`: `application/service/ConstruirCurvaService.java:155-159`

**Grava vértices**
- **Esperado:** `tDadoVertcCurva` (com `cDiaUtil`)
- **No código (prova):** Entidade e coluna `cDiaUtil`: `adapter/out/persistence/jpa/entity/DadoVertcCurvaEntity.java:19-20, 39-55`; grava pontos: `adapter/out/persistence/jpa/DadoCurvaJpaAdapter.java:51-66`
- **Bate?:** Parcial: campo mapeado; construção persiste `duPub`, não o `du` resolvido, então `cDiaUtil` pode ser nulo: `application/service/ConstruirCurvaService.java:232-262`

**Grava interpolada**
- **Esperado:** `tDadoCurva`, um por dia corrido
- **No código (prova):** Tabela/colunas: `adapter/out/persistence/jpa/entity/DadoCurvaEntity.java:18-39`; grava lista: `DadoCurvaJpaAdapter.java:81-90`; gera datas diárias inclusivas: `domain/interpolacao/InterpolacaoDadoCurva.java:21-34`
- **Bate?:** Sim quanto à grade gerada; limites dependem das políticas `Disabled` (`21-27`)

**NTN-B**
- **Esperado:** `vVertcCurva` em dias corridos, sem calendário no processor
- **No código (prova):** Interpreta o prazo como inteiro em DC (`NtnbBootstrapAnbima.java:108-116`); processor não calcula datas/calendário, só encaminha a carga à construção (`ProcessarCargaService.java:93-105`)
- **Bate?:** Não: NTN-B muda o vencimento para o dia 15 antes do ajuste, em vez de usar `base + vVertcCurva` exato (`NtnbBootstrapAnbima.java:113-116`)

**`hashPontos`**
- **Esperado:** SHA-256 de `AAAA-MM-DD;valor` canônico, separados por `\n`
- **No código (prova):** Ordena por data, normaliza zero e decimal sem expoente, une com LF e calcula SHA-256 UTF-8: `domain/curva/HashPontos.java:17-26`
- **Bate?:** Sim

## E. Nomes divergentes

- **`TaxaSwapB3` / `TAXA_SWAP_B3`** — nome na change: `ProntaTsB3` / `PRONTA_TS_B3`. Onde: `domain/construcao/TaxaSwapB3.java:13-18`; registry: `application/service/ResolverModelos.java:23-26`.
- **`PreparacaoVertices` / `VerticesPreparados`** — nome na change: `PreparacaoPontos` / `PontosPreparados`. Onde: `domain/interpolacao/PreparacaoVertices.java:15-17` (nome prescrito no guia: `implementacao.md:704-711`).
- **`ConsultarVerticesService` / `pontos`** — nome na change: `ConsultarPontosService` / `vertices`. Onde: `application/service/ConsultarVerticesService.java:21, 28-51`; rota: `adapter/in/api/rest/controller/CurvaController.java:98-106`; alvo nominal do guia: `implementacao.md:1038-1040`.
- **`DisparoConstrucao.CONSTRUCAO`** — nome na change: `DisparoConstrucao.DATA_INTEIRA`. Onde: enum atual `domain/curva/DisparoConstrucao.java:3-7`; chamada pela data `application/service/ConstruirDataService.java:80-91`; alvo `tasks.md:55`.
- **`fatorAcum` / `fatorDia`** — nome na change: `fatorAcumulado` / `fatorDiario`. Onde: `domain/interpolacao/ValorNoPrazo.java:6-15`; consulta: `application/service/ConsultarVerticesService.java:28-39`.
- **Campo JSON `pontos`** — nome na change: `vertices`. Onde: `application/service/ConsultarVerticesService.java:41-51`.

## F. Sobras (existem e a spec atual não pede)

- **`src/main/java/br/com/poc/domain/curva/Componente.java:3`; `domain/curva/CurvaComponente.java:5-10`; `application/service/ConstruirCurvaService.java:214-220`** — Tipos de composição/derivação existem; o pipeline guarda componentes, mas a resolução ainda lança `MODELO_FALHOU`. Componentes e derivadas pertencem à segunda parte (origem derivada); anotado aqui como sobra, não removido.
- **`src/main/java/br/com/poc/domain/cadastro/TabelaParametros.java:51-53`; `domain/cadastro/ValidadorCadastro.java:247-268`** — `MODELOS_POR_ORIGEM` e seleção de origem secundária também estão implementados, fora do escopo atual (a validação permite modelos alternativos para outro provedor).
- **`src/main/java/br/com/poc/adapter/in/consumer/kafka/KafkaConsumer.java:3-4`; `adapter/out/producer/kafka/KafkaProducer.java:3-4`** — Interfaces Kafka vazias; não são rotas REST nem participam do pipeline descrito.

## G. Dúvidas

- **`cDiaUtil` deve guardar o DU calculado quando a fonte não publica DU?** Hoje `DadoVerticeCurva` recebe `duPub`, portanto SOFR e outros pontos sem DU ficam nulos apesar do DU já calculado. Prova: `application/service/ConstruirCurvaService.java:232-262`; persistência do componente `DadoCurvaJpaAdapter.java:53-65`.
- **A inspeção confirma lógica de timeout da trava, mas qual configuração de isolamento está ativa no banco operacional?** Não encontrei `transaction-isolation` nem `READ_COMMITTED` em `src/main`; sem assumir o default do SQL Server, não dá para afirmar o isolamento efetivo. Prova: `adapter/out/persistence/jpa/CurvaMercadoJpaAdapter.java:94-108`; `src/main/resources/application.yml:29-48`; busca no código-fonte por `transaction-isolation`, `READ_COMMITTED`, `NOLOCK` sem resultado.
- **O calendário brasileiro implementa as regras e a massa está presente, mas a conferência de 278 DUs do teste usa `2026-09-02`, enquanto o arquivo/aceite é `2026-09-14`; falta evidência de comparação com o vetor exigido.** Prova: `src/test/java/br/com/poc/domain/construcao/VetoresReaisTest.java:117-127`; `src/test/java/br/com/poc/domain/construcao/FixturesB3.java:15-18`; `tasks.md:23`.
- **O teste que nomeia os cinco hashes não os calcula: só verifica que strings literais não estão vazias.** Prova: `src/test/java/br/com/poc/domain/construcao/VetoresReaisTest.java:160-168`.
- **`GET /curvas/{codigo}/{dataBase}` consulta vértices, mas também lê `tDadoCurva` para detectar desatualização, contradizendo literalmente "sem usar `tDadoCurva`" na consulta. A revisão deve decidir se a comparação é exceção permitida.** Prova: `application/service/ConsultarVerticesService.java:133-149`; requisito `tasks.md:49`.
- **`GET /valores-cadastro` enumera todos os valores de `Frequency`, incluindo `NoFrequency`, `Once` e `OtherFrequency`, enquanto o validador só permite a lista restrita de frequências compostas. O contrato deve dizer se o catálogo inclui também valores recusados.** Prova: `application/service/ConsultarValoresCadastroService.java:46-55, 90-97`; `domain/cadastro/TabelaParametros.java:15-17`; `domain/cadastro/ValidadorCadastro.java:188-202`.
- **Sem pontos, a regravação apaga `tDadoCurva` e lança `NotFoundException` dentro de `@Transactional`; por ser `RuntimeException`, a transação padrão tende a reverter o DELETE. Confirmar se a spec quer que o DELETE persista junto com a resposta de erro.** Prova: `application/service/RegravarInterpoladaService.java:55-65`; `application/exception/BaseException.java:31`; requisito `tasks.md:50`.
