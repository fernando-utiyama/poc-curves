package com.poccurves.api.domain;

import java.time.LocalDate;

/**
 * Uma linha do catálogo {@code tCurvaMercd} — sem estado de ciclo de vida: ao contrário do
 * antigo DefinicaoCurva (RASCUNHO/ATIVA/APOSENTADA, versionado), esta tabela já é a fonte de
 * verdade, populada por migração + pipeline de aquisição/construção, nunca por esta API.
 */
public record CurvaMercado(
        String tickerIndcd,
        String classfInstt,
        String classAtivo,
        String moedaNegoc,
        LocalDate inicVigencia,
        String usuarCalc) {
}
