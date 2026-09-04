package com.poccurves.engine.adapter.out.persistence;
import com.poccurves.engine.domain.construcao.EstadoModelo;
import com.poccurves.engine.domain.construcao.ModeloCurva;
import com.poccurves.engine.domain.construcao.TipoModelo;

import com.poccurves.engine.application.ModeloCurvaRepositoryPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistência JDBC de {@link ModeloCurva} na tabela modelo_curva (db/migration/V5__modelo_curva.sql).
 */
@Repository
public class ModeloCurvaRepository implements ModeloCurvaRepositoryPort {

    private final JdbcTemplate jdbcTemplate;

    public ModeloCurvaRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<ModeloCurva> mapper = (rs, rowNum) -> {
        Timestamp importadoEmTs = rs.getTimestamp("importado_em");
        Instant importadoEm = importadoEmTs != null ? importadoEmTs.toInstant() : null;
        return ModeloCurva.reconstituir(
                UUID.fromString(rs.getString("id")),
                rs.getString("codigo"),
                rs.getString("nome"),
                TipoModelo.valueOf(rs.getString("tipo")),
                EstadoModelo.valueOf(rs.getString("estado")),
                rs.getString("codigo_fonte"),
                rs.getString("checksum"),
                rs.getString("importado_por"),
                importadoEm
        );
    };

    @Override
    public void inserir(ModeloCurva modelo) {
        String sql = """
                INSERT INTO modelo_curva (
                    id, codigo, nome, tipo, estado, codigo_fonte, checksum, importado_por, importado_em
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        Timestamp importadoEm = modelo.importadoEm() != null ? Timestamp.from(modelo.importadoEm()) : null;
        jdbcTemplate.update(sql,
                modelo.id(),
                modelo.codigo(),
                modelo.nome(),
                modelo.tipo().name(),
                modelo.estado().name(),
                modelo.codigoFonte(),
                modelo.checksum(),
                modelo.importadoPor(),
                importadoEm
        );
    }

    @Override
    public void atualizar(ModeloCurva modelo) {
        String sql = "UPDATE modelo_curva SET estado = ? WHERE id = ?";
        jdbcTemplate.update(sql, modelo.estado().name(), modelo.id());
    }

    @Override
    public Optional<ModeloCurva> buscarPorCodigo(String codigo) {
        String sql = """
                SELECT id, codigo, nome, tipo, estado, codigo_fonte, checksum, importado_por, importado_em
                FROM modelo_curva
                WHERE codigo = ?
                """;
        List<ModeloCurva> resultados = jdbcTemplate.query(sql, mapper, codigo);
        return resultados.isEmpty() ? Optional.empty() : Optional.of(resultados.get(0));
    }

    @Override
    public List<ModeloCurva> listarAtivos() {
        String sql = """
                SELECT id, codigo, nome, tipo, estado, codigo_fonte, checksum, importado_por, importado_em
                FROM modelo_curva
                WHERE estado = 'ATIVO'
                """;
        return jdbcTemplate.query(sql, mapper);
    }

    @Override
    public List<ModeloCurva> listarTodos() {
        String sql = """
                SELECT id, codigo, nome, tipo, estado, codigo_fonte, checksum, importado_por, importado_em
                FROM modelo_curva
                ORDER BY importado_em DESC
                """;
        return jdbcTemplate.query(sql, mapper);
    }
}
