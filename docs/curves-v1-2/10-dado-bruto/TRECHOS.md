# Listagem do bruto da B3: trechos para acrescentar ou trocar (não substituem o arquivo)

Pacotes como `br.com.poc`: troque por `br.com.bradesco`. Substituem arquivos inteiros: `BtrsCurvaPrimrResumo.java`, `BtrsCurvaPrimrResumoResponse.java` e `BtrsCurvaPrimrRepositoryPort.java` (nesta pasta).

## 1. `BtrsCurvaPrimrRepository`

**Na query do `listarAgregado`, apague a linha** (o GET respeita só as regras do banco):

```sql
AND m.cTickerIdtfdUnic IS NOT NULL
```

**Acrescente o método novo**, depois do `listarAgregado` (reaproveita a `AgregadoProjection`):

```java
@Query(value = """
    SELECT m.cTickerIdtfdUnic AS codigo,
           m.cTickerIndcd AS nome,
           m.cSitReg AS situacao,
           b.dBaseReft AS dataRef,
           COUNT(*) AS quantidade,
           CASE WHEN EXISTS (SELECT 1 FROM dbo.tDadoVertcCurva v WHERE v.cTickerIndcd = m.cTickerIndcd AND v.dBaseReft = b.dBaseReft) THEN 1 ELSE 0 END AS construida
    FROM dbo.tBtrsCurvaPrimr b
    JOIN dbo.tCurvaMercd m ON m.cTickerIndcd = b.cTickerIndcd
    WHERE b.dBaseReft = (SELECT MAX(u.dBaseReft) FROM dbo.tBtrsCurvaPrimr u WHERE u.cTickerIndcd = b.cTickerIndcd)
      AND (:codigo IS NULL OR m.cTickerIdtfdUnic = :codigo)
      AND (:nome IS NULL OR UPPER(m.cTickerIndcd) LIKE UPPER(CONCAT('%', :nome, '%')))
    GROUP BY m.cTickerIdtfdUnic, m.cTickerIndcd, m.cSitReg, b.dBaseReft
    ORDER BY m.cTickerIndcd ASC
    """,
    nativeQuery = true)
List<AgregadoProjection> listarUltimaData(
    @Param("codigo") String codigo,
    @Param("nome") String nome
);
```

## 2. `BtrsCurvaPrimrPersistenceAdapter`

Troque o `listarAgregado` inteiro por estes quatro métodos (o `toDomain` e o resto ficam):

```java
@Override
public List<BtrsCurvaPrimrResumo> listarAgregado(LocalDate de, LocalDate ate, String codigo, String nome) {
    return paraResumos(repository.listarAgregado(de, ate, codigo, nome));
}

@Override
public List<BtrsCurvaPrimrResumo> listarUltimaData(String codigo, String nome) {
    return paraResumos(repository.listarUltimaData(codigo, nome));
}

private List<BtrsCurvaPrimrResumo> paraResumos(List<BtrsCurvaPrimrRepository.AgregadoProjection> linhas) {
    Map<String, List<String>> tickersPorCurva = new HashMap<>();
    if (curvaPrvdrRepositoryPort != null) {
        for (CurvaProvedor cp : curvaPrvdrRepositoryPort.buscarCurvasProvedor("B3", "TS", null)) {
            tickersPorCurva.computeIfAbsent(cp.nomeCurva(), k -> new ArrayList<>()).add(cp.codigoNaFonte());
        }
    }
    return linhas.stream()
        .map(p -> new BtrsCurvaPrimrResumo(
            p.getCodigo(),
            p.getNome(),
            situacaoOuNula(p.getSituacao()),
            p.getDataRef(),
            p.getQuantidade() != null ? p.getQuantidade() : 0L,
            tickersPorCurva.getOrDefault(p.getNome(), List.of()),
            Integer.valueOf(1).equals(p.getConstruida())))
        .toList();
}

/** Situação fora do enum vira nula, sem esconder qualquer outro erro. */
private static SituacaoCurva situacaoOuNula(String texto) {
    if (texto == null) {
        return null;
    }
    try {
        return SituacaoCurva.valueOf(texto.trim());
    } catch (IllegalArgumentException e) {
        return null;
    }
}
```

Imports que podem faltar: `java.util.*` (já tem), `br.com.poc.domain.cadastro.CurvaProvedor` (já tem) e `br.com.poc.domain.SituacaoCurva` (já tem).

## 3. `BtrsCurvaPrimrService`

Troque o `listarAgregado` inteiro (os 22 e as demais partes ficam):

```java
@Override
@Transactional(readOnly = true)
public List<BtrsCurvaPrimrResumo> listarAgregado(LocalDate de, LocalDate ate, String codigo, String nome) {
    String codigoFiltro = textoOuNulo(codigo);
    String nomeFiltro = textoOuNulo(nome);

    // sem período: uma linha por curva, com a última data gravada
    if (de == null && ate == null) {
        return btrsRepositoryPort.listarUltimaData(codigoFiltro, nomeFiltro);
    }

    LocalDate dataAte = ate != null ? ate : LocalDate.now();
    LocalDate dataDe = de != null ? de : dataAte.minusDays(30);

    if (dataDe.isAfter(dataAte)) {
        throw new InvalidInputException(CadastroErrorCode.PARAMETRO_INVALIDO);
    }
    if (ChronoUnit.DAYS.between(dataDe, dataAte) > 366) {
        throw new InvalidInputException(CadastroErrorCode.PARAMETRO_INVALIDO);
    }

    return btrsRepositoryPort.listarAgregado(dataDe, dataAte, codigoFiltro, nomeFiltro);
}

private static String textoOuNulo(String texto) {
    return texto != null && !texto.isBlank() ? texto.trim() : null;
}
```

## 4. Quem lê `codigosNaFonte()`

Use Alt+F7 em `tickersProvedor()` depois da renomeação do record: os usos que ainda chamarem `codigosNaFonte()` não compilam. O front lê o campo `tickersProvedor` no JSON.

## 5. Teste

`BtrsCurvaPrimrServiceListagemTest.java` (nesta pasta) → `src/test/java/.../application/service/` (teste novo).
