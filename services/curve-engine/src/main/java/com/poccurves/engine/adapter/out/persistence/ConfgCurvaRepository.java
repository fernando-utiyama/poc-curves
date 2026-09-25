package com.poccurves.engine.adapter.out.persistence;
import com.poccurves.engine.application.port.ConfgCurvaRepositoryPort;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Leitura JDBC de {@code tConfgCurva} (V22/V26) — resolve o {@code cMotorCalc} vigente por
 * ticker/data, igual ao padrão de vigência SCD2 usado no resto do schema legado
 * ({@code dInicVgcia}/{@code dValidAte}), desempatando por {@code cVrsaoReg} mais recente se mais
 * de uma linha vigente existir.
 */
@Repository
public class ConfgCurvaRepository implements ConfgCurvaRepositoryPort {

    private final JdbcTemplate jdbcTemplate;

    public ConfgCurvaRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<String> buscarMotorCalcVigente(String tickerIndcd, LocalDate dataReferencia) {
        List<String> resultado = jdbcTemplate.query(
                """
                SELECT TOP 1 cMotorCalc
                FROM tConfgCurva
                WHERE cTickerIndcd = ?
                  AND dInicVgcia <= ?
                  AND (dValidAte IS NULL OR dValidAte >= ?)
                ORDER BY cVrsaoReg DESC
                """,
                (rs, rowNum) -> rs.getString("cMotorCalc"),
                tickerIndcd, Date.valueOf(dataReferencia), Date.valueOf(dataReferencia));
        return resultado.stream().findFirst();
    }
}
