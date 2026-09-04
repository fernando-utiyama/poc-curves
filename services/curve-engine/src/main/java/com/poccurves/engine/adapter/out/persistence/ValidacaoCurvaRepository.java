package com.poccurves.engine.adapter.out.persistence;
import com.poccurves.engine.application.model.Classificacao;
import com.poccurves.engine.application.model.ResultadoTeste;
import com.poccurves.engine.application.model.ResultadoValidacao;
import com.poccurves.engine.application.port.ValidacaoCurvaRepositoryPort;

import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;
import java.util.UUID;

/**
 * Persistência JDBC de {@link ResultadoTeste} (com.poccurves.engine.domain) na tabela
 * validacao_curva (db/migration/V4__versao_curva.sql) — o record já mapeia 1:1 com as colunas
 * persistíveis (menos id/versao_curva_id/executado_em, geridos aqui).
 */
@Repository
public class ValidacaoCurvaRepository implements ValidacaoCurvaRepositoryPort {

    private final JdbcTemplate jdbcTemplate;

    public ValidacaoCurvaRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<ResultadoTeste> mapper = (rs, rowNum) -> new ResultadoTeste(
            rs.getString("teste"),
            Classificacao.valueOf(rs.getString("classificacao")),
            ResultadoValidacao.valueOf(rs.getString("resultado")),
            rs.getBigDecimal("medida_observada"),
            rs.getBigDecimal("limite_aplicado"),
            rs.getString("detalhe")
    );

    @Override
    public void inserirTodos(UUID versaoCurvaId, List<ResultadoTeste> resultados) {
        String sql = """
                INSERT INTO validacao_curva (
                    versao_curva_id, teste, classificacao, resultado, medida_observada, limite_aplicado, detalhe
                ) VALUES (?, ?, ?, ?, ?, ?, ?)
                """;
        jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                ResultadoTeste r = resultados.get(i);
                ps.setString(1, versaoCurvaId.toString());
                ps.setString(2, r.identificador());
                ps.setString(3, r.classificacao().name());
                ps.setString(4, r.resultado().name());
                setBigDecimalOrNull(ps, 5, r.medidaObservada());
                setBigDecimalOrNull(ps, 6, r.limiteAplicado());
                ps.setString(7, r.detalhe());
            }

            @Override
            public int getBatchSize() {
                return resultados.size();
            }

            private void setBigDecimalOrNull(PreparedStatement ps, int index, BigDecimal valor) throws SQLException {
                if (valor != null) {
                    ps.setBigDecimal(index, valor);
                } else {
                    ps.setNull(index, Types.DECIMAL);
                }
            }
        });
    }

    @Override
    public List<ResultadoTeste> buscarPorVersaoCurva(UUID versaoCurvaId) {
        String sql = """
                SELECT teste, classificacao, resultado, medida_observada, limite_aplicado, detalhe
                FROM validacao_curva
                WHERE versao_curva_id = ?
                ORDER BY id ASC
                """;
        return jdbcTemplate.query(sql, mapper, versaoCurvaId.toString());
    }
}
