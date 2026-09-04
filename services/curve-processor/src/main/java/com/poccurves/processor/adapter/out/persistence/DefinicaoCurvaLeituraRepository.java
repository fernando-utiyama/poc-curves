package com.poccurves.processor.adapter.out.persistence;

import com.poccurves.processor.application.DefinicaoCurvaLeituraRepositoryPort;
import com.poccurves.processor.domain.DefinicaoCurvaResumo;
import com.poccurves.processor.domain.ModoOrigem;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repositório somente-leitura para consulta de definição e versão vigente de curvas.
 */
@Repository
public class DefinicaoCurvaLeituraRepository implements DefinicaoCurvaLeituraRepositoryPort {

    private final JdbcTemplate jdbcTemplate;

    public DefinicaoCurvaLeituraRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<DefinicaoCurvaResumo> resolverPorCodigo(String codigo) {
        var linhas = jdbcTemplate.query(
                """
                SELECT dc.id AS definicao_curva_id, dc.codigo, dc.modo_origem,
                       vdc.id AS versao_definicao_curva_id, vdc.numero_versao
                FROM definicao_curva dc
                JOIN versao_definicao_curva vdc ON vdc.definicao_curva_id = dc.id
                WHERE dc.codigo = ? AND vdc.vigencia_fim IS NULL
                """,
                (rs, rowNum) -> new DefinicaoCurvaResumo(
                        UUID.fromString(rs.getString("definicao_curva_id")),
                        rs.getString("codigo"),
                        ModoOrigem.valueOf(rs.getString("modo_origem")),
                        UUID.fromString(rs.getString("versao_definicao_curva_id")),
                        rs.getInt("numero_versao")
                ),
                codigo);

        return linhas.stream().findFirst();
    }
}
