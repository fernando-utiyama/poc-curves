package com.poccurves.engine.adapter.out.persistence;
import com.poccurves.engine.application.model.VerticeBtrs;
import com.poccurves.engine.application.port.BtrsCurvaPrimrConsultaRepositoryPort;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;

/**
 * Leitura JDBC de {@code tBtrsCurvaPrimr} (schema legado, db/migration/V22/V23) — insumo bruto
 * gravado pelo curve-processor, consumido aqui só como leitura (fronteira de escrita daquela
 * tabela é do curve-processor).
 */
@Repository
public class BtrsCurvaPrimrConsultaRepository implements BtrsCurvaPrimrConsultaRepositoryPort {

    private final JdbcTemplate jdbcTemplate;

    public BtrsCurvaPrimrConsultaRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<VerticeBtrs> mapper = (rs, rowNum) -> new VerticeBtrs(
            rs.getInt("cDiaCorri"),
            rs.getInt("cDiaUtil"),
            rs.getBigDecimal("vPrecoTx"));

    @Override
    public List<VerticeBtrs> buscarVertices(String tickerIndcd, LocalDate dataReferencia) {
        return jdbcTemplate.query(
                "SELECT cDiaCorri, cDiaUtil, vPrecoTx FROM tBtrsCurvaPrimr WHERE cTickerIndcd = ? AND dBaseReft = ? ORDER BY cDiaUtil ASC",
                mapper, tickerIndcd, Date.valueOf(dataReferencia));
    }
}
