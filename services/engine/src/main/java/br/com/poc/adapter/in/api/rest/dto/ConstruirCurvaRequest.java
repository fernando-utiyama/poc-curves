package br.com.poc.adapter.in.api.rest.dto;

import java.time.LocalDate;

public record ConstruirCurvaRequest(
    String ticker,
    LocalDate dataBase,
    Boolean forcarRecalculo
) {}
