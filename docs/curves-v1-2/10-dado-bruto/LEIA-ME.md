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

| Arquivo | Onde |
|---|---|
| `CurvaPrimrDataBaseProjection.java` (novo: sai a interface de dentro do `BtrsCurvaPrimrRepository`) | `adapter/out/persistence/repository/` |
| `BtrsCurvaPrimrRepository.java`, `AnbmaCurvaPrimrRepository.java`, `BbergCurvaPrimrRepository.java` | `adapter/out/persistence/repository/` |
| `BtrsCurvaPrimrEntity.java`, `AnbmaCurvaPrimrEntity.java`, `BbergCurvaPrimrEntity.java` | `adapter/out/persistence/entity/` |
| `BtrsCurvaPrimrPersistenceAdapter.java`, `AnbmaCurvaPrimrPersistenceAdapter.java`, `BbergCurvaPrimrPersistenceAdapter.java`, `CurvaPrimrDataBaseMapper.java` (helper comum) | `adapter/out/persistence/` |
| `BtrsCurvaPrimrRepositoryPort.java`, `AnbmaCurvaPrimrRepositoryPort.java`, `BbergCurvaPrimrRepositoryPort.java` | `application/port/out/` |
| `CurvaPrimrDataBase.java` | `domain/cadastro/` |
| `CurvaPrimrDataBaseResponse.java` | `adapter/in/api/rest/dto/` |
| `CurvaPrimrDataBaseMapperTest.java`, `BtrsCurvaPrimrServiceListagemTest.java` | `src/test/java/...` (testes novos) |

Depois de copiar, apague os arquivos que saem: `BtrsCurvaPrimrResumo`, `AnbmaCurvaPrimrResumo`, `BbergCurvaPrimrResumo` e os `...ResumoResponse` por fonte (o `CurvaPrimrDataBase` único os substitui).

## Nomes (renomear no seu repositório com Shift+F6, para a IDE acompanhar os usos)

| Antes | Agora |
|---|---|
| `listarAgregado` (porta, repositório, adaptador, use case, service, controller) | `listarDatasBase` |
| `listarUltimaData` | `listarUltimaDataBase` |
| `existsCurvaConstruida` (repositório, porta, adaptador, service) | `existeVerticeConstruido` |
| `CurvaPrimrResumo` / `CurvaPrimrResumoResponse` | `CurvaPrimrDataBase` / `CurvaPrimrDataBaseResponse` |
| `AgregadoProjection` / `CurvaPrimrAgregadoProjection` | `CurvaPrimrDataBaseProjection` |
| `AgregadoPrimr` / `paraResumos` | `CurvaPrimrDataBaseMapper` / `paraDatasBase` |

O que a listagem devolve: por curva, **as datas que têm dado bruto gravado**, com a quantidade de vértices e se a data já foi construída. Rota e JSON não mudam (`codigo`, `nome`, `situacao`, `dataBase`, `quantidadeVertices`, `tickersProvedor`, `curvaConstruida`).

## À mão

1. **Id da B3 de `Long` para `Integer`** no record `BtrsCurvaPrimr`, no service, no controller (`@PathVariable Long id` → `Integer id`) e nos DTOs: Ctrl+Shift+F6 (Type Migration) no campo `id` do record leva a troca pelos usos. No JSON do front não muda nada.
2. **Use cases e services das 3 fontes**: `listarDatasBase` devolve `List<CurvaPrimrDataBase>`; o da B3 está abaixo. ANBIMA e Bloomberg: o mesmo, trocando o `btrsRepositoryPort` pelo da fonte.
3. **Controllers das 3 fontes**: `produces = MediaType.APPLICATION_JSON_VALUE` no `@RequestMapping` e `CurvaPrimrDataBaseResponse::fromDomain` na listagem.
4. **Bloomberg**: `reserveNextIdentifier()` virou `proximoId()` (avise o outro dev que mexeu no repositório).
5. **Testes antigos**: os que montam entidades ou usam o id da B3 como `1L` passam a `1`; stubs de `AgregadoProjection` passam a `CurvaPrimrDataBaseProjection`.

## `listarDatasBase` do service (B3)

```java
@Override
@Transactional(readOnly = true)
public List<CurvaPrimrDataBase> listarDatasBase(LocalDate de, LocalDate ate, String codigo, String nome) {
    String codigoFiltro = textoOuNulo(codigo);
    String nomeFiltro = textoOuNulo(nome);

    // sem período: uma linha por curva, com a última data gravada
    if (de == null && ate == null) {
        return btrsRepositoryPort.listarUltimaDataBase(codigoFiltro, nomeFiltro);
    }

    LocalDate dataAte = ate != null ? ate : LocalDate.now();
    LocalDate dataDe = de != null ? de : dataAte.minusDays(30);

    if (dataDe.isAfter(dataAte)) {
        throw new InvalidInputException(CadastroErrorCode.PARAMETRO_INVALIDO);
    }
    if (ChronoUnit.DAYS.between(dataDe, dataAte) > 366) {
        throw new InvalidInputException(CadastroErrorCode.PARAMETRO_INVALIDO);
    }

    return btrsRepositoryPort.listarDatasBase(dataDe, dataAte, codigoFiltro, nomeFiltro);
}

private static String textoOuNulo(String texto) {
    return texto != null && !texto.isBlank() ? texto.trim() : null;
}
```

## Conferir (supostos, sem ver o código)

- **Acessores dos records de domínio no `salvar`:** usei `diasCorridos()`, `diasUteis()`, `valor()`, `fatorAcumulado()` e `fatorDia()` (B3) e `taxa()` e `vertice()` (ANBIMA), vindos dos getters antigos da entidade. Os da Bloomberg são os da sua foto. Se o record tiver outro nome, o erro aparece no `salvar`.
- **Construtor do `BtrsCurvaPrimrService`** no `BtrsCurvaPrimrServiceListagemTest`: usei `(btrsRepositoryPort, curvaRepositoryPort, curvaPrvdrRepositoryPort, eventosPort)`. Ajuste à ordem do seu.
- **Produto dos tickers:** a B3 filtra `"B3"`/`"TS"`; ANBIMA e Bloomberg não filtram produto (`null`), como estavam.
