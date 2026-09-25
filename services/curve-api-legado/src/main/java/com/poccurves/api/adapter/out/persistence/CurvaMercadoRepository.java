package com.poccurves.api.adapter.out.persistence;

import com.poccurves.api.application.CurvaMercadoRepositoryPort;
import com.poccurves.api.domain.CurvaMercado;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Leitura JDBC do catálogo {@code tCurvaMercd} (schema legado, db/migration/V22, grants em V28) —
 * curve-api é só leitura aqui: novas curvas entram por migração + pipeline de aquisição/construção,
 * nunca por esta API.
 */
@Repository
public class CurvaMercadoRepository implements CurvaMercadoRepositoryPort {

    private final JdbcTemplate jdbcTemplate;

    public CurvaMercadoRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<CurvaMercado> mapper = (rs, rowNum) -> new CurvaMercado(
            rs.getString("cTickerIndcd"),
            rs.getString("cClasfInstt"),
            rs.getString("cClassAtivo"),
            rs.getString("cMoedaNegoc"),
            rs.getDate("dInicVgcia") != null ? rs.getDate("dInicVgcia").toLocalDate() : null,
            rs.getString("cUsuarCalc"));

    @Override
    public List<CurvaMercado> listarTodas() {
        return jdbcTemplate.query(
                "SELECT cTickerIndcd, cClasfInstt, cClassAtivo, cMoedaNegoc, dInicVgcia, cUsuarCalc " +
                        "FROM tCurvaMercd ORDER BY cTickerIndcd",
                mapper);
    }

    @Override
    public Optional<CurvaMercado> buscarPorTicker(String ticker) {
        return jdbcTemplate.query(
                        "SELECT cTickerIndcd, cClasfInstt, cClassAtivo, cMoedaNegoc, dInicVgcia, cUsuarCalc " +
                                "FROM tCurvaMercd WHERE cTickerIndcd = ?",
                        mapper, ticker)
                .stream().findFirst();
    }
}
