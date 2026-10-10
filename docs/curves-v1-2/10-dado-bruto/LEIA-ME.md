# Dado bruto (B3, ANBIMA e Bloomberg): guia único

Pacotes como `br.com.poc`: troque por `br.com.bradesco`. Todos os arquivos `.java` desta pasta são **inteiros** (copiar por cima), exceto o que está em "À mão".

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
| `BtrsCurvaPrimrService.java`, `AnbmaCurvaPrimrService.java`, `BbergCurvaPrimrService.java`, `RegrasCurvaPrimr.java` (regras comuns: período, obrigatórios, decimais, data construída) | `application/service/` |
| `BtrsCurvaPrimr.java` (id `Integer`, `dataVertice()`), `BtrsCurvaPrimrInput.java`, `AnbmaCurvaPrimrInput.java`, `BbergCurvaPrimrInput.java`, `CurvaPrimrDataBase.java`, `VerticesPrimrDaData.java` | `domain/cadastro/` |
| `BtrsCurvaPrimrController.java`, `AnbmaCurvaPrimrController.java`, `BbergCurvaPrimrController.java` | `adapter/in/api/rest/controller/` |
| `CurvaPrimrDataBaseResponse.java`, `VerticesPrimrDaDataResponse.java`, `Btrs/Anbma/BbergCurvaPrimrVerticeRequest.java`, `Btrs/Anbma/BbergCurvaPrimrVerticeResponse.java` | `adapter/in/api/rest/dto/` |
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

1. **`CadastroErrorCode`**: incluir `DATA_CONSTRUIDA("Data já construída")` e mapear para **409** no handler, igual ao `VERSAO_EM_USO`.
2. **Bloomberg**: `reserveNextIdentifier()` virou `proximoId()` (avise o outro dev que mexeu no repositório).
3. **Testes antigos** dos services e controllers do bruto: os de avisos saem; os que usavam o id da B3 como `1L` passam a `1`; o construtor dos services tem 3 argumentos (`repositoryPort` da fonte, `curvaRepositoryPort`, `eventosPort`).

## O que mudou no comportamento (para o front)

- **Rotas por nome**: `/curvas-mercado/{nome}/primaria-b3|anbima|bloomberg/{dataBase}[/vertices[/{id}]]`. A curva é buscada por `findByNome` (404 se não existir).
- **Listagem** (`GET /primaria-*`): lista simples, sem página (o front pagina na tela). Sem `de` e `ate`, a última data de cada curva; período inválido responde 400 com a mensagem explicada.
- **Consulta da data**: `{ curvaConstruida, vertices: [...] }`, sem avisos.
- **Incluir** 201 com o vértice; **alterar** 200 com o vértice; **apagar vértice** e **apagar data** 200 vazio. Erro: 422 `DADOS_INVALIDOS` com a mensagem de cada campo.
- **Apagar data já construída**: 409 `DATA_CONSTRUIDA` ("Apague antes a curva construída dessa data..."). Incluir, alterar e apagar um vértice numa data construída são aceitos: o bruto nunca reconstrói a curva; o gestor recalcula na tela Curvas.
- **Decimais em texto**, como gravados (sem arredondar), nas três fontes.
- **Campos**: B3 `diasCorridos`, `diasUteis`, `valor` (obrigatórios), `fatorAcumulado`, `fatorDia`, e `dataVertice` (só leitura). ANBIMA `prazoDiasCorridos` (obrigatório, ≥ 1), `taxa`, e `vencimento` (só leitura). Bloomberg `tickerBloomberg` e `precoUltimo` (obrigatórios), `precoLiquidacao`, `precoMedio`, `diaVencimento`, `dataLiquidacaoFinanceira`, `formaLiquidacao`, `dataVencimentoContrato`, `dataUltimoNegocio`.
- **Sem trava da curva** (`findByCodigoComLock` saiu): duas gravações ao mesmo tempo na mesma data não se bloqueiam; a última vence.

## Conferir (supostos, sem ver o código)

- **Acessores dos records de domínio**: ANBIMA `AnbmaCurvaPrimr(id, nomeCurva, dataBase, taxa, vertice)` (da sua foto); Bloomberg na ordem do adaptador (`precoLiquidacao, precoMedio, precoUltimo, diaVencimento, dataLiquidacaoFinanceira, tickerBloomberg, formaLiquidacao, dataVencimentoContrato, dataUltimoNegocio`). Se o seu record tiver outra ordem, o erro aparece no `novoVertice` do service.
- **`EventoCurvaPrimariaEditada`**: mantive os 11 argumentos que o service da B3 usava (correlationId, fonte, código, nome, data, operação, antes, depois, quantidade antes, quantidade depois, quando).
- **Obrigatórios da Bloomberg e da ANBIMA**: segui a spec `vertices-brutos-provedor` (Bloomberg: ticker e preço último; ANBIMA: prazo; taxa opcional).
