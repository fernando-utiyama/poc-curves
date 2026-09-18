package com.poccurves.engine.adapter.out.persistence;
import com.poccurves.engine.application.construcao.Vertice;
import com.poccurves.engine.application.port.VerticeCurvaRepositoryPort;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Leitura JDBC de {@link Vertice} na tabela vertice_curva (db/migration/V4__versao_curva.sql) —
 * ver {@link VerticeCurvaRepositoryPort}.
 */
@Repository
public class VerticeCurvaRepository implements VerticeCurvaRepositoryPort {

    private final JdbcTemplate jdbcTemplate;

    public VerticeCurvaRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<Vertice> mapper = (rs, rowNum) -> {
        int prazoDiasUteis = rs.getInt("prazo_dias_uteis");
        int prazoDiasCorridosValor = rs.getInt("prazo_dias_corridos");
        Integer prazoDiasCorridos = rs.wasNull() ? null : prazoDiasCorridosValor;
        java.sql.Date dataVencimento = rs.getDate("data_vencimento");
        java.math.BigDecimal taxa = rs.getBigDecimal("taxa");
        java.math.BigDecimal fatorDesconto = rs.getBigDecimal("fator_desconto");
        return new Vertice(
                prazoDiasUteis,
                prazoDiasCorridos,
                dataVencimento != null ? dataVencimento.toLocalDate() : null,
                taxa,
                fatorDesconto
        );
    };

    @Override
    public List<Vertice> buscarPorVersaoCurva(UUID versaoCurvaId) {
        String sql = """
                SELECT prazo_dias_uteis, prazo_dias_corridos, data_vencimento, taxa, fator_desconto
                FROM vertice_curva
                WHERE versao_curva_id = ?
                ORDER BY prazo_dias_uteis ASC
                """;
        return jdbcTemplate.query(sql, mapper, versaoCurvaId.toString());
    }
}
