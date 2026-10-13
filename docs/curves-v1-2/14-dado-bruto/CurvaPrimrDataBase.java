package br.com.poc.domain.cadastro;

import br.com.poc.domain.SituacaoCurva;

import java.time.LocalDate;
import java.util.List;

/** Linha da listagem do dado bruto. */
public record CurvaPrimrDataBase(
    String codigo,
    String nome,
    SituacaoCurva situacao,
    LocalDate dataBase,
    long quantidadeVertices,
    List<String> tickersProvedor,
    boolean curvaConstruida
) {}
