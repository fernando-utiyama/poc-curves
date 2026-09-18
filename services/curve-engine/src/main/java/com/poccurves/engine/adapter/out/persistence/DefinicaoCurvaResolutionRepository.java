package com.poccurves.engine.adapter.out.persistence;
import com.poccurves.engine.application.construcao.ConfiguracaoInterpolacao;
import com.poccurves.engine.application.port.DefinicaoCurvaResolutionRepositoryPort;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;


import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
public class DefinicaoCurvaResolutionRepository implements DefinicaoCurvaResolutionRepositoryPort {

    private final JdbcTemplate jdbcTemplate;

    public DefinicaoCurvaResolutionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Resolve o id de uma definicao_curva pelo código — usado para localizar a definição
     * IMPORTED irmã de uma definição BOOTSTRAPPED (campo
     * {@code codigo_curva_importada_irmao}, db/migration/V20).
     */
    @Override
    public Optional<UUID> resolverIdPorCodigo(String codigo) {
        if (codigo == null) {
            return Optional.empty();
        }
        List<String> ids = jdbcTemplate.queryForList(
                "SELECT id FROM definicao_curva WHERE codigo = ?", String.class, codigo);
        return ids.isEmpty() ? Optional.empty() : Optional.of(UUID.fromString(ids.get(0)));
    }

    /**
     * Resolve o interpolador e a política de extrapolação configurados na versão de definição
     * vigente na data. Usado pela API de interpolação (seção 10).
     */
    @Override
    public Optional<ConfiguracaoInterpolacao> resolverConfiguracaoInterpolacao(String codigoCurva, LocalDate dataReferencia) {
        if (codigoCurva == null || dataReferencia == null) {
            return Optional.empty();
        }

        List<Map<String, Object>> linhas = jdbcTemplate.queryForList(
                """
                SELECT vdc.interpolador, vdc.politica_extrapolacao
                FROM definicao_curva dc
                JOIN versao_definicao_curva vdc ON vdc.definicao_curva_id = dc.id
                WHERE dc.codigo = ?
                  AND vdc.vigencia_inicio <= ?
                  AND (vdc.vigencia_fim IS NULL OR vdc.vigencia_fim > ?)
                  AND vdc.numero_versao = (
                      SELECT MAX(vdc2.numero_versao) FROM versao_definicao_curva vdc2
                      WHERE vdc2.definicao_curva_id = dc.id
                        AND vdc2.vigencia_inicio <= ?
                        AND (vdc2.vigencia_fim IS NULL OR vdc2.vigencia_fim > ?)
                  )
                """,
                codigoCurva,
                Date.valueOf(dataReferencia),
                Date.valueOf(dataReferencia),
                Date.valueOf(dataReferencia),
                Date.valueOf(dataReferencia)
        );

        if (linhas.isEmpty()) {
            return Optional.empty();
        }

        Map<String, Object> linha = linhas.getFirst();
        return Optional.of(new ConfiguracaoInterpolacao(
                (String) linha.get("interpolador"),
                (String) linha.get("politica_extrapolacao")));
    }
}
