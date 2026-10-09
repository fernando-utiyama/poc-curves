package br.com.poc.domain.cadastro;

import br.com.poc.domain.SituacaoCurva;

import java.time.LocalDate;
import java.util.List;

/** Uma linha da listagem do dado bruto da Bloomberg: a curva, a data-base e o que existe naquela data. */
public record BbergCurvaPrimrResumo(
    String codigo,
    String nome,
    SituacaoCurva situacao,
    LocalDate dataBase,
    long quantidadeVertices,
    List<String> tickersProvedor,
    boolean curvaConstruida
) {}
