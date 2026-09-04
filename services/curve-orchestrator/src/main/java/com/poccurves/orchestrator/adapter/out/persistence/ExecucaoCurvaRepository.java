package com.poccurves.orchestrator.adapter.out.persistence;

import com.poccurves.orchestrator.application.ExecucaoCurvaRepositoryPort;
import com.poccurves.orchestrator.domain.EstadoExecucao;
import com.poccurves.orchestrator.domain.ExecucaoCurva;
import com.poccurves.orchestrator.domain.Faixa;
import com.poccurves.orchestrator.domain.LinhaExecucaoResumo;
import com.poccurves.orchestrator.domain.MomentoCurva;
import com.poccurves.orchestrator.domain.PaginaExecucoes;
import com.poccurves.orchestrator.domain.TipoDisparo;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistência JDBC de {@link ExecucaoCurva} — espelha db/migration/V3__execucao_curva.sql (tabela execucao_curva).
 * <p>
 * Os métodos {@code existeExecucaoAtivaParaConjuntoDados} e {@code existeExecucaoAtivaParaDefinicaoCurva}
 * implementam a tarefa 2.5 do backlog curve-orchestrator ("Implementar a verificação de execução ativa duplicada
 * por alvo, data e momento"). A distinção entre os dois métodos (conjunto de dados vs. definição de curva) é uma
 * interpretação necessária de "alvo", pois a entidade {@link ExecucaoCurva} possui dois campos de alvo possíveis
 * ({@code conjuntoDados} e {@code definicaoCurvaId}) para atender tanto aos fluxos de rotina por conjunto de dados
 * quanto aos fluxos de carga manual por curva específica, e o design.md do backlog não detalha qual usar quando
 * — revisar se essa decisão de negócio for esclarecida depois.
 */
@Repository
public class ExecucaoCurvaRepository implements ExecucaoCurvaRepositoryPort {

    private final JdbcTemplate jdbcTemplate;

    public ExecucaoCurvaRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<ExecucaoCurva> buscarPorId(UUID id) {
        var linhas = jdbcTemplate.query(
                """
                SELECT id, correlacao_id, execucao_pai_id, definicao_curva_id, conjunto_dados, data_referencia,
                       momento_curva, disparo, disparado_por, faixa, estado, motivo_sem_dado, horario_limite,
                       margem_segundos, duracao_por_etapa, tentativas, codigo_erro, mensagem_erro, iniciado_em, finalizado_em
                FROM execucao_curva
                WHERE id = ?
                """,
                this::mapearLinha,
                id != null ? id.toString() : null
        );

        return linhas.stream().findFirst();
    }

    @Override
    public void inserir(ExecucaoCurva execucao) {
        jdbcTemplate.update(
                """
                INSERT INTO execucao_curva
                    (id, correlacao_id, execucao_pai_id, definicao_curva_id, conjunto_dados, data_referencia,
                     momento_curva, disparo, disparado_por, faixa, estado, motivo_sem_dado, horario_limite,
                     margem_segundos, duracao_por_etapa, tentativas, codigo_erro, mensagem_erro, iniciado_em, finalizado_em)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                execucao.id().toString(),
                execucao.correlacaoId().toString(),
                execucao.execucaoPaiId() != null ? execucao.execucaoPaiId().toString() : null,
                execucao.definicaoCurvaId() != null ? execucao.definicaoCurvaId().toString() : null,
                execucao.conjuntoDados(),
                execucao.dataReferencia(),
                execucao.momentoCurva() != null ? execucao.momentoCurva().name() : null,
                execucao.disparo().name(),
                execucao.disparadoPor(),
                execucao.faixa().name(),
                execucao.estado().name(),
                execucao.motivoSemDado(),
                execucao.horarioLimite() != null ? Timestamp.from(execucao.horarioLimite()) : null,
                execucao.margemSegundos(),
                execucao.duracaoPorEtapaJson(),
                execucao.tentativas(),
                execucao.codigoErro(),
                execucao.mensagemErro(),
                execucao.iniciadoEm() != null ? Timestamp.from(execucao.iniciadoEm()) : null,
                execucao.finalizadoEm() != null ? Timestamp.from(execucao.finalizadoEm()) : null
        );
    }

    @Override
    public void atualizar(ExecucaoCurva execucao) {
        jdbcTemplate.update(
                """
                UPDATE execucao_curva
                SET estado = ?, motivo_sem_dado = ?, duracao_por_etapa = ?, tentativas = ?,
                    codigo_erro = ?, mensagem_erro = ?, finalizado_em = ?
                WHERE id = ?
                """,
                execucao.estado().name(),
                execucao.motivoSemDado(),
                execucao.duracaoPorEtapaJson(),
                execucao.tentativas(),
                execucao.codigoErro(),
                execucao.mensagemErro(),
                execucao.finalizadoEm() != null ? Timestamp.from(execucao.finalizadoEm()) : null,
                execucao.id().toString()
        );
    }

    @Override
    public boolean existeExecucaoAtivaParaConjuntoDados(String conjuntoDados, LocalDate dataReferencia, MomentoCurva momentoCurva) {
        if (conjuntoDados == null) {
            throw new IllegalArgumentException("conjuntoDados não pode ser nulo");
        }
        if (dataReferencia == null) {
            throw new IllegalArgumentException("dataReferencia não pode ser nulo");
        }
        if (momentoCurva == null) {
            throw new IllegalArgumentException("momentoCurva não pode ser nulo");
        }

        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM execucao_curva
                WHERE conjunto_dados = ? AND data_referencia = ? AND momento_curva = ?
                  AND estado NOT IN ('CONCLUIDA', 'SEM_DADO', 'FALHOU')
                """,
                Integer.class,
                conjuntoDados,
                dataReferencia,
                momentoCurva.name()
        );

        return count != null && count > 0;
    }

    @Override
    public boolean existeExecucaoAtivaParaDefinicaoCurva(UUID definicaoCurvaId, LocalDate dataReferencia, MomentoCurva momentoCurva) {
        if (definicaoCurvaId == null) {
            throw new IllegalArgumentException("definicaoCurvaId não pode ser nulo");
        }
        if (dataReferencia == null) {
            throw new IllegalArgumentException("dataReferencia não pode ser nulo");
        }
        if (momentoCurva == null) {
            throw new IllegalArgumentException("momentoCurva não pode ser nulo");
        }

        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM execucao_curva
                WHERE definicao_curva_id = ? AND data_referencia = ? AND momento_curva = ?
                  AND estado NOT IN ('CONCLUIDA', 'SEM_DADO', 'FALHOU')
                """,
                Integer.class,
                definicaoCurvaId.toString(),
                dataReferencia,
                momentoCurva.name()
        );

        return count != null && count > 0;
    }

    @Override
    public Optional<ExecucaoCurva> buscarExecucaoAtivaParaConjuntoDados(String conjuntoDados, LocalDate dataReferencia, MomentoCurva momentoCurva) {
        if (conjuntoDados == null) {
            throw new IllegalArgumentException("conjuntoDados não pode ser nulo");
        }
        if (dataReferencia == null) {
            throw new IllegalArgumentException("dataReferencia não pode ser nulo");
        }
        if (momentoCurva == null) {
            throw new IllegalArgumentException("momentoCurva não pode ser nulo");
        }

        var linhas = jdbcTemplate.query(
                """
                SELECT id, correlacao_id, execucao_pai_id, definicao_curva_id, conjunto_dados, data_referencia,
                       momento_curva, disparo, disparado_por, faixa, estado, motivo_sem_dado, horario_limite,
                       margem_segundos, duracao_por_etapa, tentativas, codigo_erro, mensagem_erro, iniciado_em, finalizado_em
                FROM execucao_curva
                WHERE conjunto_dados = ? AND data_referencia = ? AND momento_curva = ?
                  AND estado NOT IN ('CONCLUIDA', 'SEM_DADO', 'FALHOU')
                """,
                this::mapearLinha,
                conjuntoDados,
                dataReferencia,
                momentoCurva.name()
        );

        return linhas.stream().findFirst();
    }

    private ExecucaoCurva mapearLinha(ResultSet rs, int rowNum) throws SQLException {
        return ExecucaoCurva.reidratar(
                UUID.fromString(rs.getString("id")),
                UUID.fromString(rs.getString("correlacao_id")),
                rs.getObject("execucao_pai_id") != null ? UUID.fromString(rs.getString("execucao_pai_id")) : null,
                rs.getObject("definicao_curva_id") != null ? UUID.fromString(rs.getString("definicao_curva_id")) : null,
                rs.getString("conjunto_dados"),
                rs.getObject("data_referencia", LocalDate.class),
                rs.getString("momento_curva") != null ? MomentoCurva.valueOf(rs.getString("momento_curva")) : null,
                TipoDisparo.valueOf(rs.getString("disparo")),
                rs.getString("disparado_por"),
                Faixa.valueOf(rs.getString("faixa")),
                EstadoExecucao.valueOf(rs.getString("estado")),
                rs.getString("motivo_sem_dado"),
                rs.getTimestamp("horario_limite") != null ? rs.getTimestamp("horario_limite").toInstant() : null,
                rs.getObject("margem_segundos", Integer.class),
                rs.getString("duracao_por_etapa"),
                rs.getInt("tentativas"),
                rs.getString("codigo_erro"),
                rs.getString("mensagem_erro"),
                rs.getTimestamp("iniciado_em") != null ? rs.getTimestamp("iniciado_em").toInstant() : null,
                rs.getTimestamp("finalizado_em") != null ? rs.getTimestamp("finalizado_em").toInstant() : null
        );
    }

    @Override
    public PaginaExecucoes buscarComFiltros(String codigoCurva, LocalDate dataReferencia, String estado, int pagina, int tamanho) {
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        List<Object> params = new ArrayList<>();

        if (codigoCurva != null) {
            where.append(" AND (dc.codigo = ? OR ec.conjunto_dados = ?)");
            params.add(codigoCurva);
            params.add(codigoCurva);
        }
        if (dataReferencia != null) {
            where.append(" AND ec.data_referencia = ?");
            params.add(dataReferencia);
        }
        if (estado != null) {
            where.append(" AND ec.estado = ?");
            params.add(estado);
        }

        String fromClause = " FROM execucao_curva ec LEFT JOIN definicao_curva dc ON dc.id = ec.definicao_curva_id";

        Integer total = jdbcTemplate.queryForObject("SELECT COUNT(*)" + fromClause + where, Integer.class, params.toArray());

        List<Object> paramsComPaginacao = new ArrayList<>(params);
        paramsComPaginacao.add(pagina * tamanho);
        paramsComPaginacao.add(tamanho);

        List<LinhaExecucaoResumo> itens = jdbcTemplate.query(
                "SELECT ec.id, ec.correlacao_id, ec.disparo, dc.codigo AS codigo_curva, ec.conjunto_dados, "
                        + "ec.data_referencia, ec.momento_curva, ec.estado, ec.disparado_por, ec.iniciado_em, "
                        + "ec.finalizado_em, ec.mensagem_erro"
                        + fromClause + where
                        + " ORDER BY ec.iniciado_em DESC OFFSET ? ROWS FETCH NEXT ? ROWS ONLY",
                (rs, rowNum) -> new LinhaExecucaoResumo(
                        UUID.fromString(rs.getString("id")),
                        UUID.fromString(rs.getString("correlacao_id")),
                        rs.getString("disparo"),
                        rs.getString("codigo_curva") != null ? rs.getString("codigo_curva") : rs.getString("conjunto_dados"),
                        rs.getObject("data_referencia", LocalDate.class),
                        rs.getString("momento_curva"),
                        rs.getString("estado"),
                        rs.getString("disparado_por"),
                        rs.getTimestamp("iniciado_em") != null ? rs.getTimestamp("iniciado_em").toInstant() : null,
                        rs.getTimestamp("finalizado_em") != null ? rs.getTimestamp("finalizado_em").toInstant() : null,
                        rs.getString("mensagem_erro")
                ),
                paramsComPaginacao.toArray());

        return new PaginaExecucoes(itens, total != null ? total : 0);
    }

    @Override
    public List<ExecucaoCurva> buscarExecucoesEmAndamento() {
        return jdbcTemplate.query(
                """
                SELECT id, correlacao_id, execucao_pai_id, definicao_curva_id, conjunto_dados, data_referencia,
                       momento_curva, disparo, disparado_por, faixa, estado, motivo_sem_dado, horario_limite,
                       margem_segundos, duracao_por_etapa, tentativas, codigo_erro, mensagem_erro, iniciado_em, finalizado_em
                FROM execucao_curva
                WHERE estado IN ('PENDENTE','EXECUTANDO','CONSTRUINDO','EM_RISCO','ATRASADA')
                """,
                this::mapearLinha
        );
    }
}
