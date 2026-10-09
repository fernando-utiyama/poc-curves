# Listagem do bruto da B3: trechos para acrescentar ou trocar (não substituem o arquivo)

Pacotes como `br.com.poc`: troque por `br.com.bradesco`. Substituem arquivos inteiros: `BtrsCurvaPrimrResumo.java`, `BtrsCurvaPrimrResumoResponse.java` e `BtrsCurvaPrimrRepositoryPort.java` (nesta pasta).

## 1. `BtrsCurvaPrimrRepository`

Copiar o `BtrsCurvaPrimrRepository.java` desta pasta por cima do seu (`adapter/out/persistence/repository/`). Muda: sem `AND m.cTickerIdtfdUnic IS NOT NULL`, `ORDER BY` com desempate pelo nome (`m.cTickerIndcd`) e a query nova `listarUltimaData`. O resto (derivados, `proximoId`, `existsCurvaConstruida`, `AgregadoProjection`) é o que você já tem.

## 2. `BtrsCurvaPrimrPersistenceAdapter`

Copiar o `AgregadoPrimr.java` desta pasta para `adapter/out/persistence/` (helper comum às três fontes) e trocar o `listarAgregado` inteiro por estes três métodos (o `toDomain` e o resto ficam):

```java
@Override
public List<BtrsCurvaPrimrResumo> listarAgregado(LocalDate de, LocalDate ate, String codigo, String nome) {
    return paraResumos(repository.listarAgregado(de, ate, codigo, nome));
}

@Override
public List<BtrsCurvaPrimrResumo> listarUltimaData(String codigo, String nome) {
    return paraResumos(repository.listarUltimaData(codigo, nome));
}

private List<BtrsCurvaPrimrResumo> paraResumos(List<CurvaPrimrAgregadoProjection> linhas) {
    return AgregadoPrimr.paraResumos(
        linhas,
        AgregadoPrimr.tickersPorCurva(curvaPrvdrRepositoryPort, "B3", "TS"),
        BtrsCurvaPrimrResumo::new);
}
```

O `BtrsCurvaPrimrResumo::new` serve de fábrica porque o record tem os campos na ordem `codigo, nome, situacao, dataBase, quantidadePontos, tickersProvedor, curvaConstruida`. O teste `AgregadoPrimrTest.java` (nesta pasta) cobre o helper.

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
