package com.poccurves.orchestrator.adapter.out.persistence;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.poccurves.orchestrator.application.DefinicaoCurvaConsultaRepositoryPort;
import com.poccurves.orchestrator.domain.DefinicaoConsumidora;

import java.sql.Date;
import java.sql.Time;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Resolve as definições de curva BOOTSTRAPPED cuja versão vigente na data de referência
 * consome um determinado conjunto de dados — tarefa 8.1 do backlog curve-orchestrator.
 * A filtragem por 'vinculos_fonte' (array JSON) é feita em Java, não em SQL — mesmo espírito
 * de services/curve-api (DefinicaoCurvaService), que também desserializa esse campo em Java.
 */
@Repository
public class DefinicaoCurvaConsultaRepository implements DefinicaoCurvaConsultaRepositoryPort {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public DefinicaoCurvaConsultaRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<DefinicaoConsumidora> buscarDefinicoesBootstrappedQueConsomem(String conjuntoDados, LocalDate dataReferencia) {
        if (conjuntoDados == null) {
            throw new IllegalArgumentException("conjuntoDados não pode ser nulo");
        }
        if (dataReferencia == null) {
            throw new IllegalArgumentException("dataReferencia não pode ser nulo");
        }

        List<Map<String, Object>> linhas = jdbcTemplate.queryForList(
                """
                SELECT dc.id AS definicao_id, dc.codigo, dc.horario_limite_publicacao, vdc.vinculos_fonte
                FROM definicao_curva dc
                JOIN versao_definicao_curva vdc ON vdc.definicao_curva_id = dc.id
                WHERE dc.modo_origem = 'BOOTSTRAPPED'
                  AND dc.estado = 'ATIVA'
                  AND vdc.vigencia_inicio <= ?
                  AND (vdc.vigencia_fim IS NULL OR vdc.vigencia_fim > ?)
                  AND vdc.numero_versao = (
                      SELECT MAX(vdc2.numero_versao) FROM versao_definicao_curva vdc2
                      WHERE vdc2.definicao_curva_id = dc.id
                        AND vdc2.vigencia_inicio <= ?
                        AND (vdc2.vigencia_fim IS NULL OR vdc2.vigencia_fim > ?)
                  )
                """,
                Date.valueOf(dataReferencia),
                Date.valueOf(dataReferencia),
                Date.valueOf(dataReferencia),
                Date.valueOf(dataReferencia)
        );

        List<DefinicaoConsumidora> resultado = new ArrayList<>();
        for (Map<String, Object> linha : linhas) {
            String vinculosJson = (String) linha.get("vinculos_fonte");
            List<String> vinculos = desserializarVinculos(vinculosJson);
            if (vinculos.contains(conjuntoDados)) {
                resultado.add(new DefinicaoConsumidora(
                        UUID.fromString((String) linha.get("definicao_id")),
                        (String) linha.get("codigo"),
                        ((Time) linha.get("horario_limite_publicacao")).toLocalTime()
                ));
            }
        }
        return resultado;
    }

    private List<String> desserializarVinculos(String vinculosJson) {
        if (vinculosJson == null || vinculosJson.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(vinculosJson, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    /**
     * Resolve o id de uma definição de curva pelo código — usado pela carga manual (grupo 5 do
     * backlog curve-orchestrator) para vincular a execução à definição antes de acionar curve-processor.
     */
    @Override
    public Optional<UUID> buscarIdPorCodigo(String codigo) {
        if (codigo == null) {
            throw new IllegalArgumentException("codigo não pode ser nulo");
        }
        List<String> ids = jdbcTemplate.queryForList(
                "SELECT id FROM definicao_curva WHERE codigo = ?", String.class, codigo);
        return ids.stream().findFirst().map(UUID::fromString);
    }
}
