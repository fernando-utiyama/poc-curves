package br.com.poc.adapter.in.api.rest.dto;

import br.com.poc.domain.model.PontoInterpolado;

import java.time.LocalDate;
import java.util.List;

public record CalcularCurvaResponse(
    String ticker,
    LocalDate dataBase,
    int base,
    String metodoInterpolacao,
    String politicaExtrapolacao,
    List<PontoInterpolado> resultados,
    String correlationId
) {}
