package com.poccurves.engine.adapter.out.persistence;
import com.poccurves.engine.application.model.VerticeConstruido;
import com.poccurves.engine.application.port.DadoCurvaRepositoryPort;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;

/** Persistência JDBC em {@code tDadoCurva} (V22) — ver {@link DadoCurvaRepositoryPort}. */
@Repository
public class DadoCurvaRepository implements DadoCurvaRepositoryPort {

    private final JdbcTemplate jdbcTemplate;

    public DadoCurvaRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional
    public void substituirVertices(String tickerIndcd, LocalDate dataReferencia, List<VerticeConstruido> vertices) {
        jdbcTemplate.update(
                "DELETE FROM tDadoCurva WHERE cTickerIndcd = ? AND dBaseReft = ?",
                tickerIndcd, Date.valueOf(dataReferencia));

        if (vertices == null || vertices.isEmpty()) {
            return;
        }

        List<Object[]> batchArgs = vertices.stream()
                .map(v -> new Object[]{
                        Date.valueOf(dataReferencia),
                        tickerIndcd,
                        Date.valueOf(v.dataVertice()),
                        v.valor()
                })
                .toList();

        jdbcTemplate.batchUpdate(
                "INSERT INTO tDadoCurva (dBaseReft, cTickerIndcd, dVertcReft, vPrecoTx) VALUES (?, ?, ?, ?)",
                batchArgs);
    }
}
