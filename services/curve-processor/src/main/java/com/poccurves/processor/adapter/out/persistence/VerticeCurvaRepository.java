package com.poccurves.processor.adapter.out.persistence;
import com.poccurves.processor.domain.curva.VerticeCurva;

import com.poccurves.processor.application.VerticeCurvaRepositoryPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Persistência JDBC de {@link VerticeCurva} — espelha db/migration/V4__versao_curva.sql (tabela vertice_curva).
 */
@Repository
public class VerticeCurvaRepository implements VerticeCurvaRepositoryPort {

    private final JdbcTemplate jdbcTemplate;

    public VerticeCurvaRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Insere todos os vértices de uma versão de curva em lote.
     */
    @Override
    public void inserirTodos(UUID versaoCurvaId, List<VerticeCurva> vertices) {
        if (vertices == null || vertices.isEmpty()) {
            return;
        }

        List<Object[]> batchArgs = vertices.stream()
                .map(v -> new Object[]{
                        versaoCurvaId.toString(),
                        v.prazoDiasUteis(),
                        v.prazoDiasCorridos(),
                        v.dataVencimento(),
                        v.taxa(),
                        v.fatorDesconto()
                })
                .toList();

        jdbcTemplate.batchUpdate(
                """
                INSERT INTO vertice_curva (versao_curva_id, prazo_dias_uteis, prazo_dias_corridos, data_vencimento, taxa, fator_desconto)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                batchArgs);
    }
}
