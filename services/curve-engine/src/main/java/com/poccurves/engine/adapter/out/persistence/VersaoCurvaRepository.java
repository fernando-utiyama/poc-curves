package com.poccurves.engine.adapter.out.persistence;
import com.poccurves.engine.application.model.EstadoVersaoCurva;
import com.poccurves.engine.application.model.MomentoCurva;
import com.poccurves.engine.application.model.OrigemVersao;
import com.poccurves.engine.application.model.VersaoCurva;
import com.poccurves.engine.application.port.VersaoCurvaRepositoryPort;

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
    public Optional<VersaoCurva> buscarVersaoVigentePublicada(UUID definicaoCurvaId, LocalDate dataReferencia, MomentoCurva momentoCurva) {
        String sql = """
                SELECT * FROM versao_curva
                WHERE definicao_curva_id = ? AND data_referencia = ? AND momento_curva = ? AND estado = 'PUBLICADA'
                """;
        List<VersaoCurva> resultados = jdbcTemplate.query(sql, mapper, definicaoCurvaId, dataReferencia, momentoCurva.name());
        return resultados.isEmpty() ? Optional.empty() : Optional.of(resultados.get(0));
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
}
