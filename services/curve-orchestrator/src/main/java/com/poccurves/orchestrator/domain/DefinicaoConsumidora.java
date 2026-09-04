package com.poccurves.orchestrator.domain;

import java.time.LocalTime;
import java.util.UUID;

/** Definição de curva consumidora resolvida, com os dados mínimos necessários para emitir curve.build.requested.v1. */
public record DefinicaoConsumidora(UUID definicaoCurvaId, String codigo, LocalTime horarioLimitePublicacao) {}
