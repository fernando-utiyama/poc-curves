package com.poccurves.processor.adapter.out.persistence;
import com.poccurves.processor.domain.curva.MomentoCurva;

import com.poccurves.processor.application.ExecucaoCurvaLeituraRepositoryPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Leitura (SELECT-only, credencial restrita — curve_processor_app nunca
 * escreve em execucao_curva, só curve_orchestrator_app) da execução de
 * curva que disparou uma aquisição do feeder, resolvida pelo mesmo
 * correlationId que o curve-orchestrator propaga para o feeder
 * ({@code AquisicaoExecutionService.acionarFeederEEncadear}, que usa
 * {@code execucao_curva.correlacao_id} como correlationId) e que o feeder
 * propaga verbatim no envelope do evento publicado.
 * <p>
 * Se nenhuma execução for encontrada (ex.: aquisição disparada fora do
 * curve-orchestrator, um pull ad-hoc), o chamador deve tratar isso como
 * ausência de dado opcional — a ingestão bruta em ponto_dado_mercado já
 * terá sido concluída com sucesso antes desta resolução ser consultada, e
 * não publicar uma versão IMPORTADA por falta de execução vinculada não é
 * um erro.
 */
@Repository
public class ExecucaoCurvaLeituraRepository implements ExecucaoCurvaLeituraRepositoryPort {

    private final JdbcTemplate jdbcTemplate;

    public ExecucaoCurvaLeituraRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<ExecucaoCurvaResumo> buscarPorCorrelacaoId(UUID correlacaoId) {
        List<ExecucaoCurvaResumo> resultados = jdbcTemplate.query(
                "SELECT id, momento_curva FROM execucao_curva WHERE correlacao_id = ?",
                (rs, rowNum) -> {
                    String momentoTexto = rs.getString("momento_curva");
                    return new ExecucaoCurvaResumo(
                            UUID.fromString(rs.getString("id")),
                            momentoTexto != null ? MomentoCurva.valueOf(momentoTexto) : null);
                },
                correlacaoId.toString());
        return resultados.stream().findFirst();
    }
}
