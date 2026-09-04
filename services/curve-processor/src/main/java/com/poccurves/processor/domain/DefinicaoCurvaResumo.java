package com.poccurves.processor.domain;

import java.util.UUID;

/**
 * Projeção somente-leitura de definicao_curva + a versao_definicao_curva
 * vigente (vigencia_fim IS NULL), o suficiente para o curve-processor
 * resolver e publicar uma curva pronta — nunca escreve nestas duas tabelas
 * (fronteira de leitura da credencial restrita, tarefa 1.6).
 */
public record DefinicaoCurvaResumo(
        UUID definicaoCurvaId,
        String codigo,
        ModoOrigem modoOrigem,
        UUID versaoDefinicaoCurvaId,
        int numeroVersaoDefinicao
) {
}
