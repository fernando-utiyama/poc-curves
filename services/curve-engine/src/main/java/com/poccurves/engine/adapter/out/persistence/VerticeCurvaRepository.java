package com.poccurves.engine.adapter.out.persistence;
import com.poccurves.engine.application.model.Vertice;
import com.poccurves.engine.application.port.VerticeCurvaRepositoryPort;

import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

/**
 * Persistência JDBC de {@link Vertice} na tabela vertice_curva
 * (db/migration/V4__versao_curva.sql) — o record já mapeia 1:1 com as colunas
 * persistíveis (menos id/versao_curva_id, geridos aqui).
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
    public void inserirTodos(UUID versaoCurvaId, List<Vertice> vertices) {
        String sql = """
                INSERT INTO vertice_curva (
                    versao_curva_id, prazo_dias_uteis, prazo_dias_corridos, data_vencimento, taxa, fator_desconto
                ) VALUES (?, ?, ?, ?, ?, ?)
                """;
        jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                Vertice v = vertices.get(i);
                ps.setString(1, versaoCurvaId.toString());
                ps.setInt(2, v.prazoDiasUteis());
                if (v.prazoDiasCorridos() != null) {
                    ps.setInt(3, v.prazoDiasCorridos());
                } else {
                    ps.setNull(3, java.sql.Types.INTEGER);
                }
                ps.setDate(4, v.dataVencimento() != null ? java.sql.Date.valueOf(v.dataVencimento()) : null);
                ps.setBigDecimal(5, v.taxa());
                ps.setBigDecimal(6, v.fatorDesconto());
            }

            @Override
            public int getBatchSize() {
                return vertices.size();
            }
        });
    }

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
