package com.poccurves.api.adapter.out.persistence;

import com.poccurves.api.application.DefinicaoCurvaRepositoryPort;
import com.poccurves.api.domain.DefinicaoCurva;
import com.poccurves.api.domain.EstadoDefinicaoCurva;
import com.poccurves.api.domain.ModoOrigem;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class DefinicaoCurvaRepository implements DefinicaoCurvaRepositoryPort {

    private final JdbcTemplate jdbcTemplate;

    public DefinicaoCurvaRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static final RowMapper<DefinicaoCurva> ROW_MAPPER = new RowMapper<>() {
        @Override
        public DefinicaoCurva mapRow(ResultSet rs, int rowNum) throws SQLException {
            UUID id = UUID.fromString(rs.getString("id"));
            String codigo = rs.getString("codigo");
            String nome = rs.getString("nome");
            String moeda = rs.getString("moeda");
            ModoOrigem modoOrigem = ModoOrigem.valueOf(rs.getString("modo_origem"));
            LocalTime horarioLimite = rs.getTime("horario_limite_publicacao").toLocalTime();
            EstadoDefinicaoCurva estado = EstadoDefinicaoCurva.valueOf(rs.getString("estado"));
            Instant criadoEm = rs.getTimestamp("criado_em").toInstant();
            String criadoPor = rs.getString("criado_por");

            return DefinicaoCurva.reconstituir(
                    id, codigo, nome, moeda, modoOrigem, horarioLimite, estado, criadoEm, criadoPor
            );
        }
    };


    @Override
    public boolean existeCodigo(String codigo) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM definicao_curva WHERE codigo = ?",
                Integer.class,
                codigo
        );
        return count != null && count > 0;
    }

    @Override
    public Optional<DefinicaoCurva> buscarPorCodigo(String codigo) {
        try {
            DefinicaoCurva def = jdbcTemplate.queryForObject(
                    "SELECT id, codigo, nome, moeda, modo_origem, horario_limite_publicacao, estado, criado_em, criado_por FROM definicao_curva WHERE codigo = ?",
                    ROW_MAPPER,
                    codigo
            );
            return Optional.ofNullable(def);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<DefinicaoCurva> buscarPorId(UUID id) {
        try {
            DefinicaoCurva def = jdbcTemplate.queryForObject(
                    "SELECT id, codigo, nome, moeda, modo_origem, horario_limite_publicacao, estado, criado_em, criado_por FROM definicao_curva WHERE id = ?",
                    ROW_MAPPER,
                    id.toString()
            );
            return Optional.ofNullable(def);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    @Override
    public void salvar(DefinicaoCurva def) {
        jdbcTemplate.update(
                """
                INSERT INTO definicao_curva (id, codigo, nome, moeda, modo_origem, horario_limite_publicacao, estado, criado_em, criado_por)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                def.id().toString(),
                def.codigo(),
                def.nome(),
                def.moeda(),
                def.modoOrigem().name(),
                Time.valueOf(def.horarioLimitePublicacao()),
                def.estado().name(),
                Timestamp.from(def.criadoEm()),
                def.criadoPor()
        );
    }

    @Override
    public void atualizar(DefinicaoCurva def) {
        jdbcTemplate.update(
                """
                UPDATE definicao_curva
                SET nome = ?, horario_limite_publicacao = ?, estado = ?
                WHERE id = ?
                """,
                def.nome(),
                Time.valueOf(def.horarioLimitePublicacao()),
                def.estado().name(),
                def.id().toString()
        );
    }

    @Override
    public List<DefinicaoCurva> listar(
            String codigo,
            String nome,
            String moeda,
            ModoOrigem modoOrigem,
            EstadoDefinicaoCurva estado,
            int offset,
            int limit
    ) {
        StringBuilder sql = new StringBuilder("SELECT id, codigo, nome, moeda, modo_origem, horario_limite_publicacao, estado, criado_em, criado_por FROM definicao_curva WHERE 1=1 ");
        List<Object> params = new ArrayList<>();

        if (codigo != null && !codigo.isBlank()) {
            sql.append(" AND codigo LIKE ? ");
            params.add("%" + codigo.trim() + "%");
        }
        if (nome != null && !nome.isBlank()) {
            sql.append(" AND nome LIKE ? ");
            params.add("%" + nome.trim() + "%");
        }
        if (moeda != null && !moeda.isBlank()) {
            sql.append(" AND moeda = ? ");
            params.add(moeda.trim().toUpperCase());
        }
        if (modoOrigem != null) {
            sql.append(" AND modo_origem = ? ");
            params.add(modoOrigem.name());
        }
        if (estado != null) {
            sql.append(" AND estado = ? ");
            params.add(estado.name());
        }

        sql.append(" ORDER BY codigo OFFSET ? ROWS FETCH NEXT ? ROWS ONLY");
        params.add(offset);
        params.add(limit);

        return jdbcTemplate.query(sql.toString(), ROW_MAPPER, params.toArray());
    }

    @Override
    public int contar(
            String codigo,
            String nome,
            String moeda,
            ModoOrigem modoOrigem,
            EstadoDefinicaoCurva estado
    ) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(1) FROM definicao_curva WHERE 1=1 ");
        List<Object> params = new ArrayList<>();

        if (codigo != null && !codigo.isBlank()) {
            sql.append(" AND codigo LIKE ? ");
            params.add("%" + codigo.trim() + "%");
        }
        if (nome != null && !nome.isBlank()) {
            sql.append(" AND nome LIKE ? ");
            params.add("%" + nome.trim() + "%");
        }
        if (moeda != null && !moeda.isBlank()) {
            sql.append(" AND moeda = ? ");
            params.add(moeda.trim().toUpperCase());
        }
        if (modoOrigem != null) {
            sql.append(" AND modo_origem = ? ");
            params.add(modoOrigem.name());
        }
        if (estado != null) {
            sql.append(" AND estado = ? ");
            params.add(estado.name());
        }

        Integer count = jdbcTemplate.queryForObject(sql.toString(), Integer.class, params.toArray());
        return count != null ? count : 0;
    }
}
