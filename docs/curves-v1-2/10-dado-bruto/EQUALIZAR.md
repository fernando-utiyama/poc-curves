# Dado bruto: os três iguais (B3, ANBIMA e Bloomberg)

Padrão único, o que a ANBIMA e a Bloomberg já seguiam:
- **campos da entidade** = nome da coluna sem o prefixo (`cTickerIndcd` → `tickerIndcd`; colunas `d...` com "data" na frente: `dBaseReft` → `dataBaseReft`);
- **id `Integer`** (e `int proximoId()` nas portas), porque `cIdtfdUnic` é `int` nas três tabelas (script do banco);
- **repositórios com os mesmos métodos, na mesma ordem e com os mesmos nomes**: `proximoId`, `findByTickerIndcdAndDataBaseReftOrderBy<ordem>AscIdtfdUnicAsc`, `findByIdtfdUnicAndTickerIndcdAndDataBaseReft`, `deleteByIdtfdUnicAndTickerIndcdAndDataBaseReft`, `deleteByTickerIndcdAndDataBaseReft`, `existsCurvaConstruida`, `listarAgregado`, `listarUltimaData`. Só mudam a tabela e a coluna de ordem (`DiaCorri`, `VertcCurva`, `DataVctoContr`);
- **sem relação `@ManyToOne` com a curva** nas entidades do bruto: não é usada (as consultas são nativas, com `JOIN` próprio) e relação `LAZY` em entidade costuma dar `LazyInitializationException` ou erro no Jackson. Antes de apagar, Alt+F7 em `curvaMercd` para confirmar que ninguém chama `getCurvaMercd()`;
- **projection em arquivo próprio** (`CurvaPrimrAgregadoProjection`), em vez de a ANBIMA e a Bloomberg usarem a de dentro do repositório da B3.

## Arquivos (copiar por cima, pacote `br.com.poc` → `br.com.bradesco`)

| Arquivo | Onde | O que muda |
|---|---|---|
| `CurvaPrimrAgregadoProjection.java` | `adapter/out/persistence/repository/` | novo (sai a interface de dentro do `BtrsCurvaPrimrRepository`) |
| `BtrsCurvaPrimrRepository.java`, `AnbmaCurvaPrimrRepository.java`, `BbergCurvaPrimrRepository.java` | `adapter/out/persistence/repository/` | os três com a mesma forma |
| `BtrsCurvaPrimrEntity.java` | `adapter/out/persistence/entity/` | campos renomeados e id `Integer` (a ordem dos campos não mudou, o `new BtrsCurvaPrimrEntity(...)` do adaptador continua compilando) |
| `AnbmaCurvaPrimrEntity.java` | `adapter/out/persistence/entity/` | sem o `@ManyToOne(LAZY) curvaMercd` e sem o construtor escrito à mão: o `@AllArgsConstructor` passa a ter os mesmos 5 argumentos (`idtfdUnic, tickerIndcd, dataBaseReft, precoTx, vertcCurva`), então o adaptador continua compilando |
| `BbergCurvaPrimrEntity.java` | `adapter/out/persistence/entity/` | sem o `@ManyToOne(LAZY) curvaMercd`; `ultNegoc` → `dataUltNegoc` (troque `getUltNegoc`/`setUltNegoc` no adaptador). Se o adaptador usa `new BbergCurvaPrimrEntity(...)` com o `curvaMercd`, tire esse argumento |
| `BtrsCurvaPrimrRepositoryPort.java` | `application/port/out/` | `int proximoId()`, `findBy...` e `excluir` com id `Integer`; `listarUltimaData` |
| `BtrsCurvaPrimrPersistenceAdapter.java` | `adapter/out/persistence/` | **inteiro** (visto nas fotos): nomes novos do repositório e da entidade, `listarUltimaData`, resumo pelo helper `AgregadoPrimr` |
| `AnbmaCurvaPrimrPersistenceAdapter.java` | `adapter/out/persistence/` | **inteiro**, no mesmo molde |
| `BbergCurvaPrimrPersistenceAdapter.java`, `BbergCurvaPrimrRepositoryPort.java`, `BbergCurvaPrimrResumo.java` | `adapter/out/persistence/`, `application/port/out/`, `domain/cadastro/` | inteiros, no mesmo molde (o `salvar` mantém os setters, agora com `setDataUltNegoc`); `BbergCurvaPrimrResumoResponse` ganha `tickersProvedor` no lugar de `codigosNaFonte` |
| `AnbmaCurvaPrimrRepositoryPort.java`, `AnbmaCurvaPrimrResumo.java` | `application/port/out/`, `domain/cadastro/` | inteiros, deduzidos do adaptador; o resumo tem `tickersProvedor` no lugar de `codigosNaFonte` (o `AnbmaCurvaPrimrResumoResponse` precisa da mesma troca) |
| `AgregadoPrimr.java` | `adapter/out/persistence/` | usa a projection nova |

## À mão

1. ~~`BtrsCurvaPrimrPersistenceAdapter`~~ (agora é arquivo inteiro). O que ele muda, para conferir:
   - `toDomain`: `e.getId()` → `e.getIdtfdUnic()`, `getNomeCurva()` → `getTickerIndcd()`, `getDataBase()` → `getDataBaseReft()`, `getDiasCorridos()` → `getDiaCorri()`, `getDiasUteis()` → `getDiaUtil()`, `getValor()` → `getPrecoTx()`, `getFatorAcumulado()` → `getFatorAcum()` (`getFatorDia()` fica);
   - chamadas ao repositório: `findByNomeCurvaAndDataBaseOrderByDiasCorridosAscIdAsc` → `findByTickerIndcdAndDataBaseReftOrderByDiaCorriAscIdtfdUnicAsc`; `findByIdAndNomeCurvaAndDataBase` → `findByIdtfdUnicAndTickerIndcdAndDataBaseReft`; `deleteByIdAndNomeCurvaAndDataBase` → `deleteByIdtfdUnicAndTickerIndcdAndDataBaseReft`; `deleteByNomeCurvaAndDataBase` → `deleteByTickerIndcdAndDataBaseReft`;
   - `proximoId`: `Integer next = repository.proximoId(); return next != null ? next : 1;`.
2. **Id da B3 de `Long` para `Integer`** no domínio `BtrsCurvaPrimr`, no service, no controller (`@PathVariable Long id` → `Integer id`) e nos DTOs: no IntelliJ, Ctrl+Shift+F6 (Type Migration) no campo `id` do record `BtrsCurvaPrimr` leva a troca pelos usos. Na API não muda nada para o front (o id continua número no JSON).
3. **Bloomberg**: o adaptador agora é arquivo inteiro e chama `repository.proximoId()` (era `reserveNextIdentifier()`); avise o outro dev que mexeu nele.
3b. **Produto dos tickers**: B3 usa `"B3"`/`"TS"`; ANBIMA e Bloomberg continuam sem filtro de produto (`null`), como estavam. Se quiser filtrar (`"MS"`, `"BLC2"`), é só trocar o `null` no `paraResumos`. Quem mexeu nele (outro dev) precisa saber.
4. **Imports**: onde estiver `BtrsCurvaPrimrRepository.AgregadoProjection`, trocar por `CurvaPrimrAgregadoProjection`.
5. **Testes** que montam `BtrsCurvaPrimrEntity` ou usam id `Long` (`1L`) da B3: trocar para `1`.

Depois: `mvn -q compile` lista o que ainda usa os nomes antigos.

## Nomes trocados nesta rodada

- `codigosNaFonte` → `tickersProvedor` e **`quantidadePontos` → `quantidadeVertices`** na listagem do bruto (o que se conta ali são os vértices do provedor; "ponto" é o interpolado). Vale para os três resumos, os DTOs e o `AgregadoPrimr`. O front lê `quantidadeVertices` no JSON. A `quantidadePontos` da **construção** e do painel é do engine e não muda.
