package br.com.poc.adapter.in.api.rest.dto;

import br.com.poc.domain.model.MetodoInterpolacao;
import br.com.poc.domain.model.PoliticaExtrapolacao;

import java.time.LocalDate;
import java.util.List;

public record CalcularCurvaRequest(
    String ticker,
    LocalDate dataBase,
    Integer base,
    String tipoInsumo,
    MetodoInterpolacao metodoInterpolacao,
    PoliticaExtrapolacao politicaExtrapolacao,
    List<Integer> prazos
) {}
