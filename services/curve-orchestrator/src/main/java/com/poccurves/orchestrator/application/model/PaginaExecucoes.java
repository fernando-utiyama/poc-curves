package com.poccurves.orchestrator.application.model;

import java.util.List;

/** Página de {@link LinhaExecucaoResumo} com o total de elementos, para cálculo de totalPaginas por quem chama. */
public record PaginaExecucoes(List<LinhaExecucaoResumo> itens, int totalElementos) {}
