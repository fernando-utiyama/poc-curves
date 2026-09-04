package com.poccurves.orchestrator.adapter.out.persistence;

import com.poccurves.orchestrator.application.AgendamentoRepositoryPort;
import com.poccurves.orchestrator.domain.Agendamento;
import com.poccurves.orchestrator.domain.AgendamentoComUltimaExecucao;
import com.poccurves.orchestrator.domain.Faixa;
import com.poccurves.orchestrator.domain.MomentoCurva;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class AgendamentoRepository implements AgendamentoRepositoryPort {

    private final JdbcTemplate jdbcTemplate;

    public AgendamentoRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<Agendamento> rowMapper = (rs, rowNum) -> {
        String definicaoIdStr = rs.getString("definicao_curva_id");
        return Agendamento.reconstituir(
                UUID.fromString(rs.getString("id")),
                definicaoIdStr != null ? UUID.fromString(definicaoIdStr) : null,
                rs.getString("conjunto_dados"),
                MomentoCurva.valueOf(rs.getString("momento_curva")),
                Faixa.valueOf(rs.getString("faixa")),
                rs.getString("expressao_horario"),
                rs.getString("fuso_horario"),
                rs.getInt("janela_tentativa_minutos"),
                rs.getInt("intervalo_tentativa_segundos"),
                rs.getBoolean("ativo"),
                rs.getString("criado_por"),
                rs.getTimestamp("criado_em").toLocalDateTime(),
                rs.getTimestamp("atualizado_em").toLocalDateTime()
        );
    };

    @Override
    public void inserir(Agendamento agendamento) {
        String sql = """
                INSERT INTO agendamento_curva (
                    id, definicao_curva_id, conjunto_dados, momento_curva, faixa,
                    expressao_horario, fuso_horario, janela_tentativa_minutos,
                    intervalo_tentativa_segundos, ativo, criado_por, criado_em, atualizado_em
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        jdbcTemplate.update(sql,
                agendamento.id(),
                agendamento.definicaoCurvaId(),
                agendamento.conjuntoDados(),
                agendamento.momentoCurva().name(),
                agendamento.faixa().name(),
                agendamento.expressaoHorario(),
                agendamento.fusoHorario(),
                agendamento.janelaTentativaMinutos(),
                agendamento.intervaloTentativaSegundos(),
                agendamento.ativo(),
                agendamento.criadoPor(),
                agendamento.criadoEm(),
                agendamento.atualizadoEm()
        );
    }

    @Override
    public void atualizar(Agendamento agendamento) {
        String sql = """
                UPDATE agendamento_curva SET
                    expressao_horario = ?, fuso_horario = ?, janela_tentativa_minutos = ?,
                    intervalo_tentativa_segundos = ?, faixa = ?, ativo = ?, atualizado_em = ?
                WHERE id = ?
                """;
        jdbcTemplate.update(sql,
                agendamento.expressaoHorario(),
                agendamento.fusoHorario(),
                agendamento.janelaTentativaMinutos(),
                agendamento.intervaloTentativaSegundos(),
                agendamento.faixa().name(),
                agendamento.ativo(),
                agendamento.atualizadoEm(),
                agendamento.id()
        );
    }

    @Override
    public Optional<Agendamento> buscarPorId(UUID id) {
        List<Agendamento> resultados = jdbcTemplate.query("SELECT * FROM agendamento_curva WHERE id = ?", rowMapper, id);
        return resultados.isEmpty() ? Optional.empty() : Optional.of(resultados.get(0));
    }

    @Override
    public List<Agendamento> listarAtivos() {
        return jdbcTemplate.query("SELECT * FROM agendamento_curva WHERE ativo = 1", rowMapper);
    }

    @Override
    public List<Agendamento> listarTodos() {
        return jdbcTemplate.query("SELECT * FROM agendamento_curva", rowMapper);
    }

    @Override
    public void vincularExecucao(UUID execucaoId, UUID agendamentoId) {
        jdbcTemplate.update("UPDATE execucao_curva SET agendamento_id = ? WHERE id = ?", agendamentoId, execucaoId);
    }

    @Override
    public List<AgendamentoComUltimaExecucao> buscarComUltimaExecucao() {
        String sql = """
                SELECT a.*,
                       ue.estado AS ue_estado,
                       ue.iniciado_em AS ue_iniciado_em,
                       ue.finalizado_em AS ue_finalizado_em,
                       ue.motivo_sem_dado AS ue_motivo_sem_dado
                FROM agendamento_curva a
                OUTER APPLY (
                    SELECT TOP 1 *
                    FROM execucao_curva e
                    WHERE e.agendamento_id = a.id
                    ORDER BY e.iniciado_em DESC
                ) ue
                """;

        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            Agendamento agendamento = rowMapper.mapRow(rs, rowNum);
            String estado = rs.getString("ue_estado");
            LocalDateTime iniciadoEm = rs.getTimestamp("ue_iniciado_em") != null ? rs.getTimestamp("ue_iniciado_em").toLocalDateTime() : null;
            LocalDateTime finalizadoEm = rs.getTimestamp("ue_finalizado_em") != null ? rs.getTimestamp("ue_finalizado_em").toLocalDateTime() : null;
            String motivo = rs.getString("ue_motivo_sem_dado");
            return new AgendamentoComUltimaExecucao(agendamento, estado, iniciadoEm, finalizadoEm, motivo);
        });
    }
}
