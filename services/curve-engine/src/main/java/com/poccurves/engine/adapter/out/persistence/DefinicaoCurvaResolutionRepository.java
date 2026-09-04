package com.poccurves.engine.adapter.out.persistence;
import com.poccurves.engine.application.model.ConfiguracaoInterpolacao;
import com.poccurves.engine.application.model.DefinicaoResolvida;
import com.poccurves.engine.application.model.LimiteValidacao;
import com.poccurves.engine.application.port.DefinicaoCurvaResolutionRepositoryPort;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;


import java.sql.Date;
import java.sql.Time;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
public class DefinicaoCurvaResolutionRepository implements DefinicaoCurvaResolutionRepositoryPort {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public DefinicaoCurvaResolutionRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<DefinicaoResolvida> resolverVigente(String codigoCurva, LocalDate dataReferencia) {
        if (codigoCurva == null || dataReferencia == null) {
            return Optional.empty();
        }

        List<Map<String, Object>> linhas = jdbcTemplate.queryForList(
                """
                SELECT dc.id AS definicao_id, dc.codigo, dc.modo_origem, dc.horario_limite_publicacao,
                       dc.codigo_curva_importada_irmao,
                       vdc.id AS versao_id, vdc.numero_versao, vdc.modelo_curva_id, vdc.vinculos_fonte, vdc.depende_de, vdc.limites_validacao
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

        UUID definicaoId = UUID.fromString((String) linha.get("definicao_id"));
        String codigo = (String) linha.get("codigo");
        String modoOrigem = (String) linha.get("modo_origem");
        UUID versaoId = UUID.fromString((String) linha.get("versao_id"));
        int numeroVersao = (Integer) linha.get("numero_versao");

        String modeloCurvaIdStr = (String) linha.get("modelo_curva_id");
        UUID modeloCurvaId = modeloCurvaIdStr != null ? UUID.fromString(modeloCurvaIdStr) : null;

        LocalTime horarioLimite = ((Time) linha.get("horario_limite_publicacao")).toLocalTime();

        String codigoCurvaImportadaIrmao = (String) linha.get("codigo_curva_importada_irmao");

        String vinculosJson = (String) linha.get("vinculos_fonte");
        String dependenciasJson = (String) linha.get("depende_de");
        String limitesValidacaoJson = (String) linha.get("limites_validacao");

        List<String> vinculosFonte = desserializarListaString(vinculosJson);
        List<String> dependeDe = desserializarListaString(dependenciasJson);
        List<LimiteValidacao> limitesValidacao = desserializarLimitesValidacao(limitesValidacaoJson);

        return Optional.of(new DefinicaoResolvida(definicaoId, codigo, modoOrigem, versaoId, numeroVersao, modeloCurvaId, vinculosFonte, dependeDe, horarioLimite, limitesValidacao, codigoCurvaImportadaIrmao));
    }

    /**
     * Resolve o id de uma definicao_curva pelo código — usado para localizar a definição
     * IMPORTED irmã de uma definição BOOTSTRAPPED (campo
     * {@code codigo_curva_importada_irmao}, db/migration/V20), sem precisar da junção com
     * versao_definicao_curva que {@link #resolverVigente} faz.
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
     * vigente na data — consulta separada de {@link #resolverVigente} (que já tem muitos campos
     * e muitos chamadores existentes) para não forçar todo call site a passar mais dois
     * argumentos que a maioria não precisa. Usado pela API de interpolação (seção 10).
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

    private List<String> desserializarListaString(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    private List<LimiteValidacao> desserializarLimitesValidacao(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<Map<String, String>> listaMapas = objectMapper.readValue(json, new TypeReference<List<Map<String, String>>>() {});
            List<LimiteValidacao> limites = new ArrayList<>();
            for (Map<String, String> mapa : listaMapas) {
                limites.add(LimiteValidacao.desserializar(mapa.get("teste"), mapa.get("classificacao"), mapa.get("limite")));
            }
            return limites;
        } catch (Exception e) {
            return List.of();
        }
    }
}
