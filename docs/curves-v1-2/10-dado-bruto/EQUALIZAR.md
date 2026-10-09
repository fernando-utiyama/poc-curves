# Dado bruto: os três iguais (B3, ANBIMA e Bloomberg)

Padrão único, o que a ANBIMA e a Bloomberg já seguiam:
- **campos da entidade** = nome da coluna sem o prefixo (`cTickerIndcd` → `tickerIndcd`; colunas `d...` com "data" na frente: `dBaseReft` → `dataBaseReft`);
- **id `Integer`**, porque `cIdtfdUnic` é `int` nas três tabelas (script do banco);
- **repositórios com os mesmos métodos, na mesma ordem e com os mesmos nomes**: `proximoId`, `findByTickerIndcdAndDataBaseReftOrderBy<ordem>AscIdtfdUnicAsc`, `findByIdtfdUnicAndTickerIndcdAndDataBaseReft`, `deleteByIdtfdUnicAndTickerIndcdAndDataBaseReft`, `deleteByTickerIndcdAndDataBaseReft`, `existsCurvaConstruida`, `listarAgregado`, `listarUltimaData`. Só mudam a tabela e a coluna de ordem (`DiaCorri`, `VertcCurva`, `DataVctoContr`);
- **projection em arquivo próprio** (`CurvaPrimrAgregadoProjection`), em vez de a ANBIMA e a Bloomberg usarem a de dentro do repositório da B3.

## Arquivos (copiar por cima, pacote `br.com.poc` → `br.com.bradesco`)

| Arquivo | Onde | O que muda |
|---|---|---|
| `CurvaPrimrAgregadoProjection.java` | `adapter/out/persistence/repository/` | novo (sai a interface de dentro do `BtrsCurvaPrimrRepository`) |
| `BtrsCurvaPrimrRepository.java`, `AnbmaCurvaPrimrRepository.java`, `BbergCurvaPrimrRepository.java` | `adapter/out/persistence/repository/` | os três com a mesma forma |
| `BtrsCurvaPrimrEntity.java` | `adapter/out/persistence/entity/` | campos renomeados e id `Integer` (a ordem dos campos não mudou, o `new BtrsCurvaPrimrEntity(...)` do adaptador continua compilando) |
| `BtrsCurvaPrimrRepositoryPort.java` | `application/port/out/` | `proximoId`, `findBy...` e `excluir` com id `Integer` |
| `AgregadoPrimr.java` | `adapter/out/persistence/` | usa a projection nova |

## À mão

1. **`BtrsCurvaPrimrPersistenceAdapter`**:
   - `toDomain`: `e.getId()` → `e.getIdtfdUnic()`, `getNomeCurva()` → `getTickerIndcd()`, `getDataBase()` → `getDataBaseReft()`, `getDiasCorridos()` → `getDiaCorri()`, `getDiasUteis()` → `getDiaUtil()`, `getValor()` → `getPrecoTx()`, `getFatorAcumulado()` → `getFatorAcum()` (`getFatorDia()` fica);
   - chamadas ao repositório: `findByNomeCurvaAndDataBaseOrderByDiasCorridosAscIdAsc` → `findByTickerIndcdAndDataBaseReftOrderByDiaCorriAscIdtfdUnicAsc`; `findByIdAndNomeCurvaAndDataBase` → `findByIdtfdUnicAndTickerIndcdAndDataBaseReft`; `deleteByIdAndNomeCurvaAndDataBase` → `deleteByIdtfdUnicAndTickerIndcdAndDataBaseReft`; `deleteByNomeCurvaAndDataBase` → `deleteByTickerIndcdAndDataBaseReft`;
   - `proximoId`: `Integer next = repository.proximoId(); return next != null ? next : 1;`.
2. **Id da B3 de `Long` para `Integer`** no domínio `BtrsCurvaPrimr`, no service, no controller (`@PathVariable Long id` → `Integer id`) e nos DTOs: no IntelliJ, Ctrl+Shift+F6 (Type Migration) no campo `id` do record `BtrsCurvaPrimr` leva a troca pelos usos. Na API não muda nada para o front (o id continua número no JSON).
3. **Bloomberg**: `reserveNextIdentifier()` virou `proximoId()`; trocar a chamada no `BbergCurvaPrimrPersistenceAdapter` (e na porta, se ela repassa o mesmo nome). Quem mexeu nele (outro dev) precisa saber.
4. **Imports**: onde estiver `BtrsCurvaPrimrRepository.AgregadoProjection`, trocar por `CurvaPrimrAgregadoProjection`.
5. **Testes** que montam `BtrsCurvaPrimrEntity` ou usam id `Long` (`1L`) da B3: trocar para `1`.

Depois: `mvn -q compile` lista o que ainda usa os nomes antigos.
