package com.poccurves.orchestrator.adapter.out.persistence;

import com.poccurves.orchestrator.application.MetricasRepositoryPort;
import com.poccurves.orchestrator.domain.EstadoExecucao;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.LinkedHashMap;
import java.util.Map;

@Repository
public class MetricasRepository implements MetricasRepositoryPort {

    private final JdbcTemplate jdbcTemplate;

    public MetricasRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Map<String, Integer> contarPorEstado() {
        Map<String, Integer> contagem = new LinkedHashMap<>();
        for (EstadoExecucao estado : EstadoExecucao.values()) {
            contagem.put(estado.name(), 0);
        }

        jdbcTemplate.query(
                "SELECT estado, COUNT(*) as total FROM execucao_curva GROUP BY estado",
                rs -> {
                    String estado = rs.getString("estado");
                    int total = rs.getInt("total");
                    contagem.put(estado, total);
                }
        );

        return contagem;
    }

    @Override
    public Double mediaTentativas() {
        Double media = jdbcTemplate.queryForObject(
                "SELECT AVG(CAST(tentativas AS FLOAT)) FROM execucao_curva WHERE estado IN ('CONCLUIDA','SEM_DADO','FALHOU')",
                Double.class
        );
        return media != null ? media : 0.0;
    }

    @Override
    public Double duracaoMediaSegundos() {
        Double media = jdbcTemplate.queryForObject(
                "SELECT AVG(CAST(DATEDIFF(SECOND, iniciado_em, finalizado_em) AS FLOAT)) FROM execucao_curva WHERE finalizado_em IS NOT NULL",
                Double.class
        );
        return media != null ? media : 0.0;
    }
}
