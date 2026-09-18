package com.poccurves.api.adapter.out.persistence;

import com.poccurves.api.application.CurvaDadosRepositoryPort;
import com.poccurves.api.domain.PontoCurva;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;

/**
 * Leitura JDBC de {@code tDadoCurva} (vértices construídos pelo engine) e {@code tCurvaData}
 * (curva construída/interpolada) — schema legado, db/migration/V22, grants em V28. Quem escreve
 * nessas tabelas é o curve-engine (ConstrucaoCurvaB3Service); curve-api só lê.
 */
@Repository
public class CurvaDadosRepository implements CurvaDadosRepositoryPort {

    private final JdbcTemplate jdbcTemplate;

    public CurvaDadosRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<PontoCurva> mapper = (rs, rowNum) -> new PontoCurva(
            rs.getDate("dVertcReft").toLocalDate(),
            rs.getBigDecimal("vPrecoTx"));

    @Override
    public List<PontoCurva> buscarVertices(String ticker, LocalDate dataReferencia) {
        return jdbcTemplate.query(
                "SELECT dVertcReft, vPrecoTx FROM tDadoCurva WHERE cTickerIndcd = ? AND dBaseReft = ? ORDER BY dVertcReft",
                mapper, ticker, Date.valueOf(dataReferencia));
    }

    @Override
    public List<PontoCurva> buscarCurvaConstruida(String ticker, LocalDate dataReferencia) {
        return jdbcTemplate.query(
                "SELECT dVertcReft, vPrecoTx FROM tCurvaData WHERE cTickerIndcd = ? AND dBaseReft = ? ORDER BY dVertcReft",
                mapper, ticker, Date.valueOf(dataReferencia));
    }
}
