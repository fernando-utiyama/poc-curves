package com.poccurves.processor.adapter.out.persistence;

import com.poccurves.processor.application.ValidacaoCurvaRepositoryPort;
import com.poccurves.processor.domain.ResultadoTesteCarga;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Persistência JDBC de {@link ResultadoTesteCarga} — espelha db/migration/V4__versao_curva.sql (tabela validacao_curva).
 */
@Repository
public class ValidacaoCurvaRepository implements ValidacaoCurvaRepositoryPort {

    private final JdbcTemplate jdbcTemplate;

    public ValidacaoCurvaRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Insere todos os resultados de validação de carga de uma versão de curva em lote.
     */
    @Override
    public void inserirTodos(UUID versaoCurvaId, List<ResultadoTesteCarga> resultados) {
        if (resultados == null || resultados.isEmpty()) {
            return;
        }

        List<Object[]> batchArgs = resultados.stream()
                .map(r -> new Object[]{
                        versaoCurvaId.toString(),
                        r.identificador(),
                        r.classificacao().name(),
                        r.resultado().name(),
                        r.medidaObservada(),
                        r.limiteAplicado(),
                        r.detalhe()
                })
                .toList();

        jdbcTemplate.batchUpdate(
                """
                INSERT INTO validacao_curva (versao_curva_id, teste, classificacao, resultado, medida_observada, limite_aplicado, detalhe)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                batchArgs);
    }
}
