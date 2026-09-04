package com.poccurves.orchestrator.adapter.out.persistence;

import com.poccurves.orchestrator.application.BackfillRepositoryPort;
import com.poccurves.orchestrator.domain.ProgressoBackfill;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

@Repository
public class BackfillRepository implements BackfillRepositoryPort {

    private final JdbcTemplate jdbcTemplate;

    public BackfillRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void inserirBackfillExecucao(UUID execucaoCurvaId, LocalDate dataReferenciaFinal, int concorrenciaMaxima) {
        jdbcTemplate.update(
                """
                INSERT INTO backfill_execucao (execucao_curva_id, data_referencia_final, concorrencia_maxima)
                VALUES (?, ?, ?)
                """,
                execucaoCurvaId.toString(),
                dataReferenciaFinal,
                concorrenciaMaxima
        );
    }

    @Override
    public void solicitarInterrupcao(UUID execucaoCurvaId) {
        jdbcTemplate.update(
                """
                UPDATE backfill_execucao
                SET interrupcao_solicitada = 1
                WHERE execucao_curva_id = ?
                """,
                execucaoCurvaId.toString()
        );
    }

    @Override
    public boolean interrupcaoSolicitada(UUID execucaoCurvaId) {
        Boolean result = jdbcTemplate.queryForObject(
                """
                SELECT interrupcao_solicitada
                FROM backfill_execucao
                WHERE execucao_curva_id = ?
                """,
                Boolean.class,
                execucaoCurvaId.toString()
        );
        return result != null && result;
    }

    @Override
    public Optional<LocalDate> buscarDataReferenciaFinal(UUID execucaoCurvaId) {
        var linhas = jdbcTemplate.query(
                "SELECT data_referencia_final FROM backfill_execucao WHERE execucao_curva_id = ?",
                (rs, rowNum) -> rs.getObject("data_referencia_final", LocalDate.class),
                execucaoCurvaId.toString()
        );
        return linhas.stream().findFirst();
    }

    @Override
    public ProgressoBackfill calcularProgresso(UUID execucaoMaeId) {
        return jdbcTemplate.queryForObject(
                """
                SELECT
                    COUNT(*) as total,
                    SUM(CASE WHEN estado = 'CONCLUIDA' THEN 1 ELSE 0 END) as concluidas,
                    SUM(CASE WHEN estado = 'SEM_DADO' THEN 1 ELSE 0 END) as semDado,
                    SUM(CASE WHEN estado = 'FALHOU' THEN 1 ELSE 0 END) as falhas,
                    SUM(CASE WHEN estado NOT IN ('CONCLUIDA', 'SEM_DADO', 'FALHOU') THEN 1 ELSE 0 END) as pendentes
                FROM execucao_curva
                WHERE execucao_pai_id = ?
                """,
                this::mapearProgresso,
                execucaoMaeId.toString()
        );
    }

    private ProgressoBackfill mapearProgresso(ResultSet rs, int rowNum) throws SQLException {
        return new ProgressoBackfill(
                rs.getInt("total"),
                rs.getInt("concluidas"),
                rs.getInt("semDado"),
                rs.getInt("falhas"),
                rs.getInt("pendentes")
        );
    }
}
