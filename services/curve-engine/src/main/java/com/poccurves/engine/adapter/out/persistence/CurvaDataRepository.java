package com.poccurves.engine.adapter.out.persistence;
import com.poccurves.engine.application.model.VerticeConstruido;
import com.poccurves.engine.application.port.CurvaDataRepositoryPort;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;

/** Persistência JDBC em {@code tCurvaData} (V22) — ver {@link CurvaDataRepositoryPort}. */
@Repository
public class CurvaDataRepository implements CurvaDataRepositoryPort {

    private final JdbcTemplate jdbcTemplate;

    public CurvaDataRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional
    public void excluirPontos(String tickerIndcd, LocalDate dataReferencia) {
        jdbcTemplate.update(
                "DELETE FROM tCurvaData WHERE cTickerIndcd = ? AND dBaseReft = ?",
                tickerIndcd, Date.valueOf(dataReferencia));
    }

    @Override
    @Transactional
    public void inserirPontos(String tickerIndcd, LocalDate dataReferencia, List<VerticeConstruido> pontos) {
        if (pontos == null || pontos.isEmpty()) {
            return;
        }

        List<Object[]> batchArgs = pontos.stream()
                .map(p -> new Object[]{
                        Date.valueOf(dataReferencia),
                        tickerIndcd,
                        Date.valueOf(p.dataVertice()),
                        p.valor()
                })
                .toList();

        jdbcTemplate.batchUpdate(
                "INSERT INTO tCurvaData (dBaseReft, cTickerIndcd, dVertcReft, vPrecoTx) VALUES (?, ?, ?, ?)",
                batchArgs);
    }
}
