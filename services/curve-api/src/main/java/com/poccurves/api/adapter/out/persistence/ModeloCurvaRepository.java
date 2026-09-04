package com.poccurves.api.adapter.out.persistence;

import com.poccurves.api.application.ModeloCurvaRepositoryPort;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public class ModeloCurvaRepository implements ModeloCurvaRepositoryPort {

    private final JdbcTemplate jdbcTemplate;

    public ModeloCurvaRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<ModeloCurvaRegistro> buscarPorId(UUID id) {
        try {
            var reg = jdbcTemplate.queryForObject(
                    "SELECT id, codigo, nome, tipo, estado, codigo_fonte, checksum FROM modelo_curva WHERE id = ?",
                    (rs, rowNum) -> new ModeloCurvaRegistro(
                            UUID.fromString(rs.getString("id")),
                            rs.getString("codigo"),
                            rs.getString("nome"),
                            rs.getString("tipo"),
                            rs.getString("estado"),
                            rs.getString("codigo_fonte"),
                            rs.getString("checksum")
                    ),
                    id.toString()
            );
            return Optional.ofNullable(reg);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<ModeloCurvaRegistro> buscarPorCodigo(String codigo) {
        try {
            var reg = jdbcTemplate.queryForObject(
                    "SELECT id, codigo, nome, tipo, estado, codigo_fonte, checksum FROM modelo_curva WHERE codigo = ?",
                    (rs, rowNum) -> new ModeloCurvaRegistro(
                            UUID.fromString(rs.getString("id")),
                            rs.getString("codigo"),
                            rs.getString("nome"),
                            rs.getString("tipo"),
                            rs.getString("estado"),
                            rs.getString("codigo_fonte"),
                            rs.getString("checksum")
                    ),
                    codigo
            );
            return Optional.ofNullable(reg);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }
}
