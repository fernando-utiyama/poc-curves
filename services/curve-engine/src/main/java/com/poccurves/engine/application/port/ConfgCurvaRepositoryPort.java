package com.poccurves.engine.application.port;

import java.time.LocalDate;
import java.util.Optional;

/**
 * Leitura da configuração por curva em {@code tConfgCurva} (V22) — hoje só o campo usado pela
 * construção das curvas TS B3: {@code cMotorCalc}, o identificador do {@code Interpolador}
 * registrado (openspec/changes/b3-additional-curves) que essa curva usa. Cada
 * {@code cTickerIndcd} tem no máximo uma linha vigente por data (vigência
 * {@code dInicVgcia}/{@code dValidAte}, igual ao padrão SCD2 do restante do schema legado).
 */
public interface ConfgCurvaRepositoryPort {

    /** Identificador do motor de cálculo (interpolador) vigente para {@code tickerIndcd} em {@code dataReferencia}. */
    Optional<String> buscarMotorCalcVigente(String tickerIndcd, LocalDate dataReferencia);
}
