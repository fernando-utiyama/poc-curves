package com.poccurves.processor.adapter.out.persistence;
import com.poccurves.processor.application.model.B3TaxaSwapParser.VerticeTaxaSwap;
import com.poccurves.processor.application.port.BtrsCurvaPrimrRepositoryPort;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;

/**
 * Persistência JDBC em {@code tBtrsCurvaPrimr} (schema legado, db/migration/V22/V23) — espelha
 * exatamente as colunas replicadas do script real (openspec/changes/legado-schema-curvas-mercado).
 */
@Repository
public class BtrsCurvaPrimrRepository implements BtrsCurvaPrimrRepositoryPort {

    private final JdbcTemplate jdbcTemplate;

    public BtrsCurvaPrimrRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional
    public void substituirVertices(String tickerIndcd, LocalDate dataReferencia, List<VerticeTaxaSwap> vertices) {
        jdbcTemplate.update(
                "DELETE FROM tBtrsCurvaPrimr WHERE cTickerIndcd = ? AND dBaseReft = ?",
                tickerIndcd, Date.valueOf(dataReferencia));

        if (vertices == null || vertices.isEmpty()) {
            return;
        }

        List<Object[]> batchArgs = vertices.stream()
                .map(v -> new Object[]{
                        tickerIndcd,
                        v.diasCorridos(),
                        v.diasUteis(),
                        Date.valueOf(dataReferencia),
                        v.taxa()
                })
                .toList();

        jdbcTemplate.batchUpdate(
                """
                INSERT INTO tBtrsCurvaPrimr (cldtfdUnic, cTickerIndcd, cDiaCorri, cDiaUtil, dBaseReft, vPrecoTx)
                VALUES (NEXT VALUE FOR seq_tbtrscurvaprimr_cidtfdunic, ?, ?, ?, ?, ?)
                """,
                batchArgs);
    }
}
