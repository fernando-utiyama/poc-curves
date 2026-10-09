package br.com.poc.domain.cadastro;

import br.com.poc.domain.SituacaoCurva;

import java.time.LocalDate;
import java.util.List;

/** Uma linha da listagem do dado bruto da ANBIMA: a curva, a data-base e o que existe naquela data. */
public record AnbmaCurvaPrimrResumo(
    String codigo,
    String nome,
    SituacaoCurva situacao,
    LocalDate dataBase,
    long quantidadePontos,
    List<String> tickersProvedor,
    boolean curvaConstruida
) {}
