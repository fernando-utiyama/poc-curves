# Dado bruto (B3, ANBIMA e Bloomberg): guia único

Pacotes como `br.com.poc`: troque pelo pacote do seu repositório. Todos os arquivos `.java` desta pasta são **inteiros** (copiar por cima), exceto o que está em "À mão".

## Padrão das três fontes

- Campos da entidade = nome da coluna sem o prefixo (`cTickerIndcd` → `tickerIndcd`); colunas `d...` com "data" na frente (`dBaseReft` → `dataBaseReft`).
- Id `Integer` (a coluna `cIdtfdUnic` é `int`) e `int proximoId()` nas portas.
- Repositórios com os mesmos métodos e nomes: `proximoId`, `findByTickerIndcdAndDataBaseReftOrderBy<ordem>AscIdtfdUnicAsc`, `findByIdtfdUnicAndTickerIndcdAndDataBaseReft`, `deleteByIdtfdUnicAndTickerIndcdAndDataBaseReft`, `deleteByTickerIndcdAndDataBaseReft`, `existeVerticeConstruido`, `listarDatasBase`, `listarUltimaDataBase`. Só muda a tabela e a coluna de ordem (`DiaCorri`, `VertcCurva`, `DataVctoContr`).
- Sem `@ManyToOne` com a curva nas entidades (não era usado; relação `LAZY` em entidade costuma dar `LazyInitializationException` ou erro no Jackson).
- Um resumo para as três fontes: `CurvaPrimrDataBase` e `CurvaPrimrDataBaseResponse`, com `quantidadeVertices` e `tickersProvedor`.
- A listagem não esconde curva sem código; sem `de` e `ate`, devolve a última data de cada curva.

## Arquivos

Agora a pasta tem as **três camadas** de cada fonte (persistência, service/use case e controller). Copie por cima; os nomes que mudaram estão em "Nomes".

| Arquivo | Onde |
|---|---|
| `CurvaPrimrDataBaseProjection.java` | `adapter/out/persistence/repository/` |
| `BtrsCurvaPrimrRepository.java`, `AnbmaCurvaPrimrRepository.java`, `BbergCurvaPrimrRepository.java` | `adapter/out/persistence/repository/` |
| `BtrsCurvaPrimrEntity.java`, `AnbmaCurvaPrimrEntity.java`, `BbergCurvaPrimrEntity.java` | `adapter/out/persistence/entity/` |
| `BtrsCurvaPrimrPersistenceAdapter.java`, `AnbmaCurvaPrimrPersistenceAdapter.java`, `BbergCurvaPrimrPersistenceAdapter.java`, `CurvaPrimrDataBaseMapper.java` | `adapter/out/persistence/` |
| `BtrsCurvaPrimrRepositoryPort.java`, `AnbmaCurvaPrimrRepositoryPort.java`, `BbergCurvaPrimrRepositoryPort.java` | `application/port/out/` |
| `BtrsCurvaPrimrUseCase.java`, `AnbmaCurvaPrimrUseCase.java`, `BbergCurvaPrimrUseCase.java` | `application/port/in/usecase/` |
| `BtrsCurvaPrimrService.java`, `AnbmaCurvaPrimrService.java`, `BbergCurvaPrimrService.java`, `RegrasCurvaPrimr.java` (regras comuns: período, obrigatórios, decimais) | `application/service/` |
| `BtrsCurvaPrimr.java` (id `Integer`, `dataVertice()`), `BtrsCurvaPrimrInput.java`, `AnbmaCurvaPrimrInput.java`, `BbergCurvaPrimrInput.java`, `CurvaPrimrDataBase.java`, `VerticesPrimrDaData.java` | `domain/cadastro/` |
| `BtrsCurvaPrimrController.java`, `AnbmaCurvaPrimrController.java`, `BbergCurvaPrimrController.java` | `adapter/in/api/rest/controller/` |
| `CurvaPrimrDataBaseResponse.java`, `VerticesPrimrDaDataResponse.java` | `adapter/in/api/rest/dto/` |
| `CurvaPrimrDataBaseMapperTest.java`, `BtrsCurvaPrimrServiceListagemTest.java`, `BtrsCurvaPrimrServiceTest.java` | `src/test/java/...` |

Os records `AnbmaCurvaPrimr` e `BbergCurvaPrimr` ficam os seus (não estão aqui).

**Apague** depois de copiar (ficam sem uso): `BtrsCurvaPrimrResumo`, `AnbmaCurvaPrimrResumo`, `BbergCurvaPrimrResumo`, os `...ResumoResponse` por fonte, `BtrsCurvaPrimrResultado`, `BtrsCurvaPrimrDataResponse`, `BtrsCurvaPrimrExclusaoResponse`, `BtrsCurvaPrimrLinhaComAvisosResponse`, `BtrsCurvaPrimrLinhaRequest`, `BtrsCurvaPrimrLinhaResponse` e os equivalentes da ANBIMA e da Bloomberg (Request/Response/Resultado antigos). No `CodigoAvisoCurva`, saem os avisos do bruto (`DIAS_CORRIDOS_NAO_POSITIVO`, `DIAS_UTEIS_INCOERENTES`, `DIAS_CORRIDOS_REPETIDOS`, `CURVA_SEM_PROVEDOR_B3`, `CURVA_JA_CONSTRUIDA` e os parecidos das outras fontes). Confirme cada um com Alt+F7 antes de apagar.

## Nomes (renomear no seu repositório com Shift+F6, para a IDE acompanhar os usos)

| Antes | Agora |
|---|---|
| `listarAgregado` (porta, repositório, adaptador, use case, service, controller) | `listarDatasBase` |
| `listarUltimaData` | `listarUltimaDataBase` |
| `existsCurvaConstruida` (repositório, porta, adaptador, service) | `existeVerticeConstruido` |
| `CurvaPrimrResumo` / `CurvaPrimrResumoResponse` | `CurvaPrimrDataBase` / `CurvaPrimrDataBaseResponse` |
| `AgregadoProjection` / `CurvaPrimrAgregadoProjection` | `CurvaPrimrDataBaseProjection` |
| `AgregadoPrimr` / `paraResumos` | `CurvaPrimrDataBaseMapper` / `paraDatasBase` |
| `consultarLinhasPorData`, `incluirLinha`, `alterarLinha`, `excluirLinha` (use case, service, controller) | `consultar`, `incluir`, `alterar`, `excluir` |
| `BtrsCurvaPrimrLinhaRequest` / `BtrsCurvaPrimrLinhaResponse` | `BtrsCurvaPrimrVerticeRequest` / `BtrsCurvaPrimrVerticeResponse` |
| `dataPonto()` (record da B3) | `dataVertice()` |

O que a listagem devolve: por curva, **as datas que têm dado bruto gravado**, com a quantidade de vértices e se a data já foi construída. Rota e JSON não mudam (`codigo`, `nome`, `situacao`, `dataBase`, `quantidadeVertices`, `tickersProvedor`, `curvaConstruida`).

## À mão

1. **Bloomberg**: `reserveNextIdentifier()` virou `proximoId()` (avise o outro dev que mexeu no repositório).
2. **Testes antigos** dos services e controllers do bruto: os de avisos saem; os que usavam o id da B3 como `1L` passam a `1`; o construtor dos services tem 3 argumentos (`repositoryPort` da fonte, `curvaRepositoryPort`, `eventosPort`).

## O que mudou no comportamento (para o front)

- **Rotas por nome**: `/curvas-mercado/{nome}/primaria-b3|anbima|bloomberg/{dataBase}[/vertices[/{id}]]`. A curva é buscada por `findByNome` (404 se não existir).
- **Listagem** (`GET /primaria-*`): lista simples, sem página (o front pagina na tela). Sem `de` e `ate`, a última data de cada curva; período inválido responde 400 com a mensagem explicada.
- **Consulta da data**: `{ curvaConstruida, vertices: [...] }`, sem avisos.
- **Incluir** 201 com o vértice; **alterar** 200 com o vértice; **apagar vértice** e **apagar data** 200 vazio. Erro: 422 `DADOS_INVALIDOS` com a mensagem de cada campo.
- **Apagar a data** apaga só o bruto, mesmo que a data já tenha sido construída: a curva construída não muda. O mesmo vale para incluir, alterar e apagar um vértice; o bruto nunca reconstrói nem apaga a curva. O front avisa o gestor pelo `curvaConstruida` da consulta.
- **Decimais em texto**, como gravados (sem arredondar), nas três fontes.
- **Campos**: B3 `diasCorridos`, `diasUteis`, `valor` (obrigatórios), `fatorAcumulado`, `fatorDia`, e `dataVertice` (só leitura). ANBIMA `prazoDiasCorridos` (obrigatório, ≥ 1), `taxa`, e `vencimento` (só leitura). Bloomberg `tickerBloomberg` e `precoUltimo` (obrigatórios), `precoLiquidacao`, `precoMedio`, `diaVencimento`, `dataLiquidacaoFinanceira`, `formaLiquidacao`, `dataVencimentoContrato`, `dataUltimoNegocio`.
- **Sem trava da curva** (`findByCodigoComLock` saiu): duas gravações ao mesmo tempo na mesma data não se bloqueiam; a última vence.

## Conferir (supostos, sem ver o código)

- **Acessores dos records de domínio**: ANBIMA `AnbmaCurvaPrimr(id, nomeCurva, dataBase, taxa, vertice)` (da sua foto); Bloomberg na ordem do adaptador (`precoLiquidacao, precoMedio, precoUltimo, diaVencimento, dataLiquidacaoFinanceira, tickerBloomberg, formaLiquidacao, dataVencimentoContrato, dataUltimoNegocio`). Se o seu record tiver outra ordem, o erro aparece no `novoVertice` do service.
- **`EventoCurvaPrimariaEditada`**: mantive os 11 argumentos que o service da B3 usava (correlationId, fonte, código, nome, data, operação, antes, depois, quantidade antes, quantidade depois, quando).
- **Obrigatórios da Bloomberg e da ANBIMA**: segui a spec `vertices-brutos-provedor` (Bloomberg: ticker e preço último; ANBIMA: prazo; taxa opcional).

## No fim da 14: apagar o que sobrou dos avisos (veio da `aplicado/13-limpeza`)

Com as primárias copiadas, apague, com Alt+F7 confirmando 0 usos:
- `AvisoCurva` e `CodigoAvisoCurva` (`domain/aviso/`);
- no `ValidadorParametros` (já sem avisos desde a `aplicado/13-limpeza`), as constantes `NATIVE_CONSTRUCTION_MODELS`, `NATIVE_INTERPOLATORS` e `NATIVE_CALENDARS`, se nada mais as usar;
- no `CadastroErrorCode`, os códigos de aviso que sobrarem sem uso.

## Por último: `InvalidInputException` no `RegrasCurvaPrimr` (erro de compilação)

No seu repo, o `InvalidInputException` recebe o `ErrorCode` (o enum), e não a `String` do código. Nas duas exceções do `Periodo` (período invertido e período acima de 366 dias), troque `CadastroErrorCode.PARAMETRO_INVALIDO.getCode()` por `CadastroErrorCode.PARAMETRO_INVALIDO`. O `NotFoundException` dos services continua com `.getCode()`, como está.

## Por último (2): sem o `BRASILIA` (a zona já está no `Application`)

O `Application` fixa o fuso do JVM (`TimeZone.setDefault` para `America/Sao_Paulo` no `main`) e recusa subir em outro fuso (`IllegalStateException` no `run`). Por isso a constante `BRASILIA` do `RegrasCurvaPrimr` sobra: `LocalDate.now()` e `OffsetDateTime.now()` já devolvem o horário de Brasília, como no resto da aplicação.

- `RegrasCurvaPrimr`: sai a constante e o `import java.time.ZoneId`; o padrão do `Periodo` usa `LocalDate.now()`.
- Os 3 services (`Btrs`, `Anbma`, `Bberg`): o `publicarEvento` usa `OffsetDateTime.now()`.

Copie os 4 arquivos desta pasta por cima, ou troque à mão. A `15-dado-bruto-alter` já está sem o `BRASILIA`.

## Por último (3): Request e Response ficam os seus; `textoOuNulo` saiu

**`...VerticeRequest` e `...VerticeResponse` (B3, ANBIMA e Bloomberg): não copie.** Os meus não tinham os `@Schema` (descrição e exemplo) e os `@Size` que os seus têm, e apagariam a documentação do Swagger. Eles saíram desta pasta. Use os seus, renomeando de `...LinhaRequest` e `...LinhaResponse` se ainda tiverem esse nome (Shift+F6), e só confira o que cada um precisa para a `14` compilar:

| Arquivo | O que precisa ter |
|---|---|
| `BtrsCurvaPrimrVerticeRequest` | `toDomain()` = `new BtrsCurvaPrimrInput(diasCorridos, diasUteis, valor, fatorAcumulado, fatorDia)` |
| `AnbmaCurvaPrimrVerticeRequest` | `toDomain()` = `new AnbmaCurvaPrimrInput(prazoDiasCorridos, taxa)` |
| `BbergCurvaPrimrVerticeRequest` | `toDomain()` = `new BbergCurvaPrimrInput(tickerBloomberg, precoUltimo, precoLiquidacao, precoMedio, diaVencimento, dataLiquidacaoFinanceira, formaLiquidacao, dataVencimentoContrato, dataUltimoNegocio)` |
| `BtrsCurvaPrimrVerticeResponse` | `fromDomain(BtrsCurvaPrimr)`: `id`, `diasCorridos`, `diasUteis`, `dataVertice` (data), `valor`, `fatorAcumulado`, `fatorDia` (os três decimais como texto, `toPlainString`) |
| `AnbmaCurvaPrimrVerticeResponse` | `fromDomain(AnbmaCurvaPrimr)`: `id`, `prazoDiasCorridos` (do `vertice`), `vencimento` (`dataBase + prazo`), `taxa` como texto |
| `BbergCurvaPrimrVerticeResponse` | `fromDomain(BbergCurvaPrimr)`: `id`, `tickerBloomberg`, `precoUltimo`, `precoLiquidacao`, `precoMedio` (como texto), `diaVencimento`, `dataLiquidacaoFinanceira`, `formaLiquidacao`, `dataVencimentoContrato`, `dataUltimoNegocio` |

**`textoOuNulo` saiu do `RegrasCurvaPrimr`.** Nos 3 services, o filtro da listagem agora é o `StringUtils.hasText` do Spring:

```java
String codigoFiltro = StringUtils.hasText(codigo) ? codigo.trim() : null;
String nomeFiltro = StringUtils.hasText(nome) ? nome.trim() : null;
```

Import: `org.springframework.util.StringUtils`. Copie os 4 arquivos (`RegrasCurvaPrimr` e os 3 services) por cima, ou troque à mão.

## Por último (4): `@Schema` nos dois DTOs novos

`CurvaPrimrDataBaseResponse` e `VerticesPrimrDaDataResponse` ganham `@Schema` (descrição e exemplo por campo), no mesmo estilo dos seus Requests. Copie os 2 arquivos desta pasta por cima. Import: `io.swagger.v3.oas.annotations.media.Schema`.

## Por último (5): Swagger simples nos 3 controllers do bruto

Copie os 3 controllers (`Btrs`, `Anbma` e `Bberg`) por cima. Mudou só a documentação:
- no nível da classe, as respostas de erro `404` (curva ou vértice não encontrado) e `422` (dados inválidos, a mensagem diz o campo);
- `@Parameter` com descrição (e exemplo) em `nome`, `dataBase`, `id` e nos filtros da listagem (`de`, `ate`, `codigo`, `nome`);
- o "Apagar vértice" ganhou uma descrição.

Imports: `io.swagger.v3.oas.annotations.Parameter`, `...responses.ApiResponse` e `...responses.ApiResponses`. A `15-dado-bruto-alter` já tem os mesmos controllers com isso e com o id `Long`.

## Por último (6): Swagger simples nos controllers de curva de mercado

Mesmo padrão do (5), agora nos 4 controllers do cadastro: `CurvaMercadoController`, `CurvaProvedorController`, `CurvaMercadoAcoesController` e `ConfiguracaoCurvaController`. Cada um ganha:
- respostas de erro no nível da classe (404 e 422; 409 onde há conflito: código ou nome em uso e curva com dados vinculados, no cadastro; provedor ou prioridade em uso, nos provedores; versão em uso, na configuração);
- `@Parameter` com descrição (e exemplo) em `nome`, `dataBase`, `idCurvaProvedor` e nos filtros e parâmetros de consulta.

**Não copie por cima sem comparar**: são os arquivos que você já aplicou e ajustou (por exemplo, o `@Tag` do `CurvaMercadoAcoesController` na sua tela é "Gestão das curvas"). Use o diff do IDE (Clipboard vs arquivo) e leve só os `@Parameter`, o `@ApiResponses` e os 3 imports (`io.swagger.v3.oas.annotations.Parameter`, `...responses.ApiResponse` e `...responses.ApiResponses`).

## Por último (7): o que fazer à mão, passo a passo (sem dúvida)

Siga nesta ordem. O que não está aqui é copiar por cima (tabela "Arquivos" e as seções "Por último" anteriores).

### Passo 1. Renomear no IDE, antes de colar
Para cada linha da tabela "Nomes": no seu repositório, clique no **nome na declaração** (a classe, o método ou o campo), aperte **Shift+F6**, digite o nome novo e Enter. O IDE troca em todos os usos, inclusive nos testes. Faça um de cada vez.
- métodos: `listarAgregado` para `listarDatasBase`, `listarUltimaData` para `listarUltimaDataBase`, `existsCurvaConstruida` para `existeVerticeConstruido`, e `consultarLinhasPorData`, `incluirLinha`, `alterarLinha`, `excluirLinha` para `consultar`, `incluir`, `alterar`, `excluir`;
- classes: `CurvaPrimrResumo` para `CurvaPrimrDataBase`, `CurvaPrimrResumoResponse` para `CurvaPrimrDataBaseResponse`, `AgregadoProjection` (ou `CurvaPrimrAgregadoProjection`) para `CurvaPrimrDataBaseProjection`, `AgregadoPrimr` para `CurvaPrimrDataBaseMapper`;
- `BtrsCurvaPrimrLinhaRequest` e `BtrsCurvaPrimrLinhaResponse` para `BtrsCurvaPrimrVerticeRequest` e `BtrsCurvaPrimrVerticeResponse` (e os equivalentes da ANBIMA e da Bloomberg, se ainda tiverem o nome `Linha`);
- `dataPonto()` do record da B3 para `dataVertice()`;
- Bloomberg: `reserveNextIdentifier()` para `proximoId()` (porta, adaptador e usos). Avise quem mexe nesse repositório.

### Passo 2. Colar os arquivos
Copie os arquivos desta pasta por cima dos seus, **menos** os que estão no Passo 3. Cole de baixo para cima (domínio, porta, persistência, service, controller) para o IDE não encher de erro no meio. Inclui os 4 arquivos do "Por último (2)" e (3) (`RegrasCurvaPrimr` e os 3 services) e os 2 DTOs do (4).

### Passo 3. Arquivos que ficam os seus (confira, não copie)
- **`...VerticeRequest` (3):** o `toDomain()` tem que chamar o `Input` da fonte com estes argumentos, nesta ordem: B3 `(diasCorridos, diasUteis, valor, fatorAcumulado, fatorDia)`; ANBIMA `(prazoDiasCorridos, taxa)`; Bloomberg `(tickerBloomberg, precoUltimo, precoLiquidacao, precoMedio, diaVencimento, dataLiquidacaoFinanceira, formaLiquidacao, dataVencimentoContrato, dataUltimoNegocio)`.
- **`...VerticeResponse` (3):** o `fromDomain` tem os campos da tabela do "Por último (3)".
- **Records `AnbmaCurvaPrimr` e `BbergCurvaPrimr`:** os services os constroem na ordem ANBIMA `(id, nomeCurva, dataBase, taxa, vertice)` e Bloomberg `(id, nomeCurva, dataBase, precoLiquidacao, precoMedio, precoUltimo, diaVencimento, dataLiquidacaoFinanceira, tickerBloomberg, formaLiquidacao, dataVencimentoContrato, dataUltimoNegocio)`. Se o seu for outra ordem, o erro aparece no `novoVertice` do service e no `toDomain` do adaptador: ajuste lá.
- **`EventoCurvaPrimariaEditada`:** o seu record tem 10 componentes e os services passam 11 (o 4º é `curva.nome()`). No record, **acrescente `String nome` como 4º componente** (Alt+Shift+Enter no erro e "Add 'String' as 4th record component", depois renomeie o parâmetro para `nome`). Se algum teste ou outro lugar criar o evento, acrescente o argumento lá.

### Passo 4. Testes
- Copie `CurvaPrimrDataBaseMapperTest`, `BtrsCurvaPrimrServiceListagemTest` e `BtrsCurvaPrimrServiceTest`.
- Nos testes antigos dos services e controllers do bruto: apague os de avisos; troque os ids `1L` da B3 por `1`; o construtor dos 3 services tem 3 argumentos, `new XCurvaPrimrService(xRepositoryPort, curvaRepositoryPort, eventosPort)`.

### Passo 5. Apagar o que ficou sem uso
Para cada classe da lista "Apague" acima: clique no nome, **Alt+F7** (Find Usages). Se não houver usos (fora dela mesma), **Alt+Delete** (Safe Delete). No `CodigoAvisoCurva`, **não apague nada agora**: os valores de aviso do bruto e a própria classe saem no fim (seção "No fim da 14").

### Passo 6. Compilar e testar
1. Compile o projeto e rode os testes. Erros comuns e a causa: `Expected 10 arguments but found 11` é o evento (Passo 3); `Required type ErrorCode, provided String` é o `InvalidInputException` (já corrigido no `RegrasCurvaPrimr` desta pasta); acessor que não existe no record é o Passo 3.
2. Suba a aplicação e teste, nas 3 fontes: a listagem sem `de` e `ate` e com período; a consulta de uma data; incluir (201), alterar (200), apagar vértice (200) e apagar a data (200); uma curva que não existe (404).

## Por último (8): o evento `CURVA_PRIMARIA_EDITADA` e o `nome` (adaptador não muda)

O `EventoCurvaPrimariaEditada` vai para o **log**: o `LogCloudEventosAdapter` registra um `afterCommit` e escreve `log.warn("{} {}", "CURVA_PRIMARIA_EDITADA", json)`, onde o `json` é o record inteiro serializado pelo `JsonMapper`. Por isso, para o `nome` (Passo 3 do "Por último (7)"):

1. No record `EventoCurvaPrimariaEditada`, acrescente `String nome` como 4º componente (depois do código da curva). O Jackson o serializa sozinho, e o log passa a ter `"nome": "DIxPRE"`.
2. **Não mude** o `LogCloudEventosAdapter` nem o `EventosPort`: o adaptador não monta campo por campo.
3. Só se algum teste ou outro lugar criar o evento à mão, acrescente o argumento lá.

Quem busca `CURVA_PRIMARIA_EDITADA` no log passa a ter um campo a mais; nada quebra.

## Por último (9): testes da B3 num arquivo só

O `BtrsCurvaPrimrServiceListagemTest` **não existe mais** nesta pasta: os 6 testes de listagem (última data, filtros aparados, período, só `ate`, início depois do fim e período maior que 366 dias) foram para o `BtrsCurvaPrimrServiceTest`, que agora cobre o service da B3 inteiro (consultar, incluir, alterar, excluir e listar). A fixture da listagem virou o campo `dataBaseListada`.

- Copie só o `BtrsCurvaPrimrServiceTest.java` por cima do seu.
- Onde a tabela "Arquivos" e o Passo 4 do "Por último (7)" citam o `...ListagemTest`, ignore: se você já o tinha colado, apague-o.
- A `15-dado-bruto-alter` tem o mesmo teste, com o id `Long`.

## Por último (10): `AnbmaCurvaPrimrVerticeRequest` com o contrato da spec

O seu Request da ANBIMA ainda tem o contrato antigo (`@NotNull BigDecimal vertice` e `@NotNull BigDecimal taxa`), e o `AnbmaCurvaPrimrInput` da `14` recebe `(Integer prazoDiasCorridos, BigDecimal taxa)`. Por isso o `toDomain()` não compila (`Required type: Integer, provided: BigDecimal`). Copie o `AnbmaCurvaPrimrVerticeRequest.java` desta pasta por cima do seu (mantive os seus `@Schema` e o `@NotNull`):
- `vertice` (BigDecimal) vira `prazoDiasCorridos` (**Integer**, obrigatório);
- `taxa` deixa de ser `@NotNull` (é opcional na spec: o processor grava `null` quando a ANBIMA não traz taxa).

**Front:** no corpo do incluir e do alterar da ANBIMA, o campo `vertice` passa a se chamar `prazoDiasCorridos`. A spec do front já usa esse nome. O seu `AnbmaCurvaPrimrVerticeResponse` precisa devolver `prazoDiasCorridos`, `vencimento` e `taxa` (tabela do "Por último (3)").

## Por último (11): `BtrsCurvaPrimrVerticeRequest` com `@Schema`

O `BtrsCurvaPrimrVerticeRequest` da B3 que você tem já compila com a `14` (o `toDomain()` bate com o `BtrsCurvaPrimrInput`). Esta versão só acrescenta o `@Schema` (descrição e exemplo) e o `@NotNull` nos três campos obrigatórios (`diasCorridos`, `diasUteis` e `valor`), no mesmo estilo dos da ANBIMA e da Bloomberg. Copie por cima do seu, se quiser a documentação.

## Por último (12): `@Schema` nos 4 Requests do cadastro

`CurvaMercadoRequest`, `CriarCurvaProvedorRequest`, `AtualizarCurvaProvedorRequest` e `CriarConfiguracaoCurvaRequest` ganham `@Schema` (descrição e exemplo por campo), no mesmo estilo dos do dado bruto. Copie os 4 por cima dos seus. Mantive o que você tem: `@JsonInclude(ALWAYS)` nos dois de provedor, `@JsonFormat` no `inicioVigencia` da configuração e o `toInput()` e os `toDomain()` iguais. Não acrescentei validação (`@NotNull`), porque os services já validam e devolvem a mensagem de cada campo. Imports novos: `io.swagger.v3.oas.annotations.media.Schema`.



## Por último (13): `ValoresService` sem avisos (e as constantes `NATIVE_*`)

O `ValoresService` (rota `GET /api/v1/curvas-mercado/valores`) ainda usava `AvisoCurva` e `CodigoAvisoCurva`: a resposta tinha o campo `avisos` (sempre vazio) e o catálogo `"avisos"` (`itemAviso` e `itensAvisos`). Copie o `ValoresService.java` desta pasta por cima do seu. Mudou só:
- `RespostaValores` agora é `RespostaValores(Map<String, Object> valores)`, sem `avisos`;
- saíram `itemAviso`, `itensAvisos`, o `resultado.put("avisos", ...)` e os imports de `AvisoCurva` e `CodigoAvisoCurva`.

**À mão (precisa conferir):**
1. **`ValoresController`:** onde ele usa `.avisos()` da resposta (a sua foto mostra 1 uso do campo), tire. Se ele devolve o record inteiro, o JSON passa de `{ "valores": {...}, "avisos": [] }` para `{ "valores": {...} }`: confirme se o front lê `avisos` de `/valores`.
2. **Chaves de mensagem:** no `messages*.properties`, apague as chaves `curvas.avisos.*.mensagem`.
3. **`ValoresServiceTest`:** tire as asserções de `avisos`.

**As constantes `NATIVE_*` do `ValidadorParametros` NÃO são todas sobra** (corrige o "No fim da 14"): o `ValoresService` usa `NATIVE_CONSTRUCTION_MODELS` (modelos de construção) e `NATIVE_CALENDARS` (calendários) para montar a lista de valores aceitos. Só o `NATIVE_INTERPOLATORS` parece ficar sem uso (a lista de interpoladores vem de `INTERPOLADORES_DISPONIVEIS`): apague-o só se o Alt+F7 mostrar 0 usos. No fim da 14, apague `AvisoCurva` e `CodigoAvisoCurva` depois de tirar estes usos.
