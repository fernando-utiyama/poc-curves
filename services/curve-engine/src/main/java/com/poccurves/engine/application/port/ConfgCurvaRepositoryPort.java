package com.poccurves.engine.application.port;

import java.time.LocalDate;
import java.util.Optional;

/**
 * Leitura da configuração por curva em {@code tConfgCurva} (V22) — hoje só o campo usado pela
 * construção das curvas TS B3: {@code cMotorCalc}, o {@code codigo} do {@code ModeloCurva}
 * (BUILTIN ou GROOVY, openspec/changes/b3-additional-curves) que essa curva usa para se
 * construir — resolvido via {@code ModeloConstrucaoResolver}, mesma infraestrutura plugável já
 * usada pela curva DI1 histórica. Cada {@code cTickerIndcd} tem no máximo uma linha vigente por
 * data (vigência {@code dInicVgcia}/{@code dValidAte}, igual ao padrão SCD2 do restante do
 * schema legado).
 */
public interface ConfgCurvaRepositoryPort {

    /** {@code codigo} do {@link com.poccurves.engine.application.model.ModeloCurva} vigente para {@code tickerIndcd} em {@code dataReferencia}. */
    Optional<String> buscarMotorCalcVigente(String tickerIndcd, LocalDate dataReferencia);
}
