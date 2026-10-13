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

