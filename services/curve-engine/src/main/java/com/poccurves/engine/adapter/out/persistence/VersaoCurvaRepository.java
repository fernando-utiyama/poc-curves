package com.poccurves.engine.adapter.out.persistence;
import com.poccurves.engine.application.exception.VersaoJaExisteException;
import com.poccurves.engine.application.model.EstadoVersaoCurva;
import com.poccurves.engine.application.model.MomentoCurva;
import com.poccurves.engine.application.model.OrigemVersao;
import com.poccurves.engine.application.model.VersaoCurva;
import com.poccurves.engine.application.port.VersaoCurvaRepositoryPort;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistência JDBC de {@link VersaoCurva} na tabela versao_curva (db/migration/V4__versao_curva.sql). */
@Repository
public class VersaoCurvaRepository implements VersaoCurvaRepositoryPort {

    private final JdbcTemplate jdbcTemplate;

    public VersaoCurvaRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<VersaoCurva> mapper = (rs, rowNum) -> {
        Timestamp publicadoEmTs = rs.getTimestamp("publicado_em");
        Instant publicadoEm = publicadoEmTs != null ? publicadoEmTs.toInstant() : null;

        return VersaoCurva.reconstituir(
                UUID.fromString(rs.getString("id")),
                UUID.fromString(rs.getString("definicao_curva_id")),
                UUID.fromString(rs.getString("versao_definicao_curva_id")),
                rs.getDate("data_referencia").toLocalDate(),
                MomentoCurva.valueOf(rs.getString("momento_curva")),
                rs.getInt("numero_versao"),
                OrigemVersao.valueOf(rs.getString("origem_versao")),
                EstadoVersaoCurva.valueOf(rs.getString("estado")),
                UUID.fromString(rs.getString("execucao_curva_id")),
                publicadoEm
        );
    };

    @Override
    public void inserir(VersaoCurva versao) {
        String sql = """
                INSERT INTO versao_curva (
                    id, definicao_curva_id, versao_definicao_curva_id, data_referencia,
                    momento_curva, numero_versao, origem_versao, estado,
                    execucao_curva_id, publicado_em
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        Timestamp publicadoEm = versao.publicadoEm() != null ? Timestamp.from(versao.publicadoEm()) : null;
        try {
            jdbcTemplate.update(sql,
                    versao.id(),
                    versao.definicaoCurvaId(),
                    versao.versaoDefinicaoCurvaId(),
                    versao.dataReferencia(),
                    versao.momentoCurva().name(),
                    versao.numeroVersao(),
                    versao.origemVersao().name(),
                    versao.estado().name(),
                    versao.execucaoCurvaId(),
                    publicadoEm
            );
        } catch (DuplicateKeyException e) {
            throw new VersaoJaExisteException("versão de curva já existe para executionId=" + versao.execucaoCurvaId());
        }
    }

    @Override
    public void atualizar(VersaoCurva versao) {
        String sql = """
                UPDATE versao_curva
                SET estado = ?, publicado_em = ?
                WHERE id = ?
                """;
        Timestamp publicadoEm = versao.publicadoEm() != null ? Timestamp.from(versao.publicadoEm()) : null;
        jdbcTemplate.update(sql,
                versao.estado().name(),
                publicadoEm,
                versao.id()
        );
    }

    @Override
    public Optional<VersaoCurva> buscarPorId(UUID id) {
        String sql = "SELECT * FROM versao_curva WHERE id = ?";
        List<VersaoCurva> resultados = jdbcTemplate.query(sql, mapper, id);
        return resultados.isEmpty() ? Optional.empty() : Optional.of(resultados.get(0));
    }

    @Override
    public Optional<VersaoCurva> buscarVersaoVigentePublicada(UUID definicaoCurvaId, LocalDate dataReferencia, MomentoCurva momentoCurva) {
        String sql = """
                SELECT * FROM versao_curva
                WHERE definicao_curva_id = ? AND data_referencia = ? AND momento_curva = ? AND estado = 'PUBLICADA'
                """;
        List<VersaoCurva> resultados = jdbcTemplate.query(sql, mapper, definicaoCurvaId, dataReferencia, momentoCurva.name());
        return resultados.isEmpty() ? Optional.empty() : Optional.of(resultados.get(0));
    }

    @Override
    public Optional<VersaoCurva> buscarUltimaVersaoPublicadaAnterior(UUID definicaoCurvaId, LocalDate dataReferenciaAntesDe, MomentoCurva momentoCurva) {
        String sql = """
                SELECT TOP 1 * FROM versao_curva
                WHERE definicao_curva_id = ? AND data_referencia < ? AND momento_curva = ? AND estado = 'PUBLICADA'
                ORDER BY data_referencia DESC
                """;
        List<VersaoCurva> resultados = jdbcTemplate.query(sql, mapper, definicaoCurvaId, dataReferenciaAntesDe, momentoCurva.name());
        return resultados.isEmpty() ? Optional.empty() : Optional.of(resultados.get(0));
    }

    @Override
    public int proximoNumeroVersao(UUID definicaoCurvaId, LocalDate dataReferencia, MomentoCurva momentoCurva) {
        String sql = """
                SELECT ISNULL(MAX(numero_versao), 0) + 1 FROM versao_curva
                WHERE definicao_curva_id = ? AND data_referencia = ? AND momento_curva = ?
                """;
        Integer max = jdbcTemplate.queryForObject(sql, Integer.class, definicaoCurvaId, dataReferencia, momentoCurva.name());
        return max != null ? max : 1;
    }

    /**
     * Resolve uma versão específica pelo número (seleção explícita de versão — tarefa 10.2 da
     * API de interpolação), não necessariamente a `PUBLICADA` vigente.
     */
    @Override
    public Optional<VersaoCurva> buscarPorNumeroVersao(UUID definicaoCurvaId, LocalDate dataReferencia, MomentoCurva momentoCurva, int numeroVersao) {
        String sql = """
                SELECT * FROM versao_curva
                WHERE definicao_curva_id = ? AND data_referencia = ? AND momento_curva = ? AND numero_versao = ?
                """;
        List<VersaoCurva> resultados = jdbcTemplate.query(sql, mapper, definicaoCurvaId, dataReferencia, momentoCurva.name(), numeroVersao);
        return resultados.isEmpty() ? Optional.empty() : Optional.of(resultados.get(0));
    }

    @Override
    public Optional<VersaoCurva> buscarPorExecucaoCurvaId(UUID execucaoCurvaId) {
        String sql = "SELECT * FROM versao_curva WHERE execucao_curva_id = ?";
        List<VersaoCurva> resultados = jdbcTemplate.query(sql, mapper, execucaoCurvaId);
        return resultados.isEmpty() ? Optional.empty() : Optional.of(resultados.get(0));
    }
}
