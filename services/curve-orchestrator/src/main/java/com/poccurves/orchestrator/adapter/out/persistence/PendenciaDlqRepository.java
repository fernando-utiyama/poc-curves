package com.poccurves.orchestrator.adapter.out.persistence;

import com.poccurves.orchestrator.application.PendenciaDlqRepositoryPort;
import com.poccurves.orchestrator.domain.EstadoPendenciaDlq;
import com.poccurves.orchestrator.domain.PendenciaDlq;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistência JDBC de {@link PendenciaDlq} — espelha db/migration/V6__pendencia_dlq.sql (tabela pendencia_dlq),
 * base para o grupo 10 do backlog curve-orchestrator (gestão de pendências de dead-letter).
 */
@Repository
public class PendenciaDlqRepository implements PendenciaDlqRepositoryPort {

    private final JdbcTemplate jdbcTemplate;

    public PendenciaDlqRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void inserir(PendenciaDlq pendencia) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    """
                    INSERT INTO pendencia_dlq
                        (id_evento, correlacao_id, motivo, detalhe, fonte, conjunto_dados, data_referencia,
                         topico_origem, particao_origem, offset_origem, topico_dlq, particao_dlq, offset_dlq,
                         grupo_consumo, versao_aplicacao, falhou_em, tentativas, estado, desfecho_em, responsavel, justificativa)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, pendencia.idEvento());
            if (pendencia.correlacaoId() != null) {
                ps.setString(2, pendencia.correlacaoId().toString());
            } else {
                ps.setNull(2, Types.VARCHAR);
            }
            ps.setString(3, pendencia.motivo());
            ps.setString(4, pendencia.detalhe());
            ps.setString(5, pendencia.fonte());
            ps.setString(6, pendencia.conjuntoDados());
            ps.setObject(7, pendencia.dataReferencia());
            ps.setString(8, pendencia.topicoOrigem());
            if (pendencia.particaoOrigem() != null) {
                ps.setInt(9, pendencia.particaoOrigem());
            } else {
                ps.setNull(9, Types.INTEGER);
            }
            if (pendencia.offsetOrigem() != null) {
                ps.setLong(10, pendencia.offsetOrigem());
            } else {
                ps.setNull(10, Types.BIGINT);
            }
            ps.setString(11, pendencia.topicoDlq());
            if (pendencia.particaoDlq() != null) {
                ps.setInt(12, pendencia.particaoDlq());
            } else {
                ps.setNull(12, Types.INTEGER);
            }
            if (pendencia.offsetDlq() != null) {
                ps.setLong(13, pendencia.offsetDlq());
            } else {
                ps.setNull(13, Types.BIGINT);
            }
            ps.setString(14, pendencia.grupoConsumo());
            ps.setString(15, pendencia.versaoAplicacao());
            ps.setTimestamp(16, Timestamp.from(pendencia.falhouEm()));
            ps.setInt(17, pendencia.tentativas());
            ps.setString(18, pendencia.estado().name());
            if (pendencia.desfechoEm() != null) {
                ps.setTimestamp(19, Timestamp.from(pendencia.desfechoEm()));
            } else {
                ps.setNull(19, Types.TIMESTAMP);
            }
            ps.setString(20, pendencia.responsavel());
            ps.setString(21, pendencia.justificativa());
            return ps;
        }, keyHolder);

        pendencia.atribuirId(keyHolder.getKey().longValue());
    }

    @Override
    public void atualizar(PendenciaDlq pendencia) {
        jdbcTemplate.update(
                """
                UPDATE pendencia_dlq
                SET tentativas = ?, estado = ?, desfecho_em = ?, responsavel = ?, justificativa = ?
                WHERE id = ?
                """,
                pendencia.tentativas(),
                pendencia.estado().name(),
                pendencia.desfechoEm() != null ? Timestamp.from(pendencia.desfechoEm()) : null,
                pendencia.responsavel(),
                pendencia.justificativa(),
                pendencia.id());
    }

    @Override
    public Optional<PendenciaDlq> buscarPorIdEvento(String idEvento) {
        var linhas = jdbcTemplate.query(
                """
                SELECT id, id_evento, correlacao_id, motivo, detalhe, fonte, conjunto_dados, data_referencia,
                       topico_origem, particao_origem, offset_origem, topico_dlq, particao_dlq, offset_dlq,
                       grupo_consumo, versao_aplicacao, falhou_em, tentativas, estado, desfecho_em,
                       responsavel, justificativa
                FROM pendencia_dlq
                WHERE id_evento = ?
                """,
                this::mapearLinha,
                idEvento);

        return linhas.stream().findFirst();
    }

    @Override
    public Optional<PendenciaDlq> buscarPorId(Long id) {
        var linhas = jdbcTemplate.query(
                """
                SELECT id, id_evento, correlacao_id, motivo, detalhe, fonte, conjunto_dados, data_referencia,
                       topico_origem, particao_origem, offset_origem, topico_dlq, particao_dlq, offset_dlq,
                       grupo_consumo, versao_aplicacao, falhou_em, tentativas, estado, desfecho_em,
                       responsavel, justificativa
                FROM pendencia_dlq
                WHERE id = ?
                """,
                this::mapearLinha,
                id);

        return linhas.stream().findFirst();
    }

    private PendenciaDlq mapearLinha(ResultSet rs, int rowNum) throws SQLException {
        return PendenciaDlq.reidratar(
                rs.getLong("id"),
                rs.getString("id_evento"),
                rs.getString("correlacao_id") != null ? UUID.fromString(rs.getString("correlacao_id")) : null,
                rs.getString("motivo"),
                rs.getString("detalhe"),
                rs.getString("fonte"),
                rs.getString("conjunto_dados"),
                rs.getObject("data_referencia", LocalDate.class),
                rs.getString("topico_origem"),
                rs.getObject("particao_origem") != null ? rs.getInt("particao_origem") : null,
                rs.getObject("offset_origem") != null ? rs.getLong("offset_origem") : null,
                rs.getString("topico_dlq"),
                rs.getObject("particao_dlq") != null ? rs.getInt("particao_dlq") : null,
                rs.getObject("offset_dlq") != null ? rs.getLong("offset_dlq") : null,
                rs.getString("grupo_consumo"),
                rs.getString("versao_aplicacao"),
                rs.getTimestamp("falhou_em").toInstant(),
                rs.getInt("tentativas"),
                EstadoPendenciaDlq.valueOf(rs.getString("estado")),
                rs.getTimestamp("desfecho_em") != null ? rs.getTimestamp("desfecho_em").toInstant() : null,
                rs.getString("responsavel"),
                rs.getString("justificativa")
        );
    }
}
