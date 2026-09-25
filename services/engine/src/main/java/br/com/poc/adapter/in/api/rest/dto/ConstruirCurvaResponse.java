package br.com.poc.adapter.in.api.rest.dto;

import java.time.LocalDate;

public record ConstruirCurvaResponse(
    String ticker,
    LocalDate dataBase,
    String estrategiaAplicada,
    int quantidadeVerticesCalculados,
    long tempoExecucaoMs,
    String correlationId
) {}
