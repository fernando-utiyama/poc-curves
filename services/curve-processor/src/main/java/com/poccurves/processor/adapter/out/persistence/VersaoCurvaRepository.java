package com.poccurves.processor.adapter.out.persistence;
import com.poccurves.processor.domain.curva.EstadoVersaoCurva;
import com.poccurves.processor.domain.curva.MomentoCurva;
import com.poccurves.processor.domain.curva.OrigemVersao;
import com.poccurves.processor.domain.curva.VersaoCurva;

import com.poccurves.processor.application.VersaoCurvaRepositoryPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Types;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/** Persistência JDBC de {@link VersaoCurva} — espelha db/migration/V4__versao_curva.sql (tabela versao_curva). */
@Repository
public class VersaoCurvaRepository implements VersaoCurvaRepositoryPort {

    private final JdbcTemplate jdbcTemplate;

    public VersaoCurvaRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Insere uma nova versão de curva no banco. */
    @Override
    public void inserir(VersaoCurva versao) {
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    """
                    INSERT INTO versao_curva
                        (id, definicao_curva_id, versao_definicao_curva_id, data_referencia, momento_curva,
                         numero_versao, origem_versao, estado, execucao_curva_id, publicado_em)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """);
            ps.setString(1, versao.id().toString());
            ps.setString(2, versao.definicaoCurvaId().toString());
            ps.setString(3, versao.versaoDefinicaoCurvaId().toString());
            ps.setObject(4, versao.dataReferencia());
            ps.setString(5, versao.momentoCurva().name());
            ps.setInt(6, versao.numeroVersao());
            ps.setString(7, versao.origemVersao().name());
            ps.setString(8, versao.estado().name());
            ps.setString(9, versao.execucaoCurvaId().toString());
            if (versao.publicadoEm() != null) {
                ps.setObject(10, java.sql.Timestamp.from(versao.publicadoEm()));
            } else {
                ps.setNull(10, Types.TIMESTAMP);
            }
            return ps;
        });
    }

    /** Atualiza o estado e o instante de publicação da versão. */
    @Override
    public void atualizar(VersaoCurva versao) {
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    """
                    UPDATE versao_curva
                    SET estado = ?, publicado_em = ?
                    WHERE id = ?
                    """);
            ps.setString(1, versao.estado().name());
            if (versao.publicadoEm() != null) {
                ps.setObject(2, java.sql.Timestamp.from(versao.publicadoEm()));
            } else {
                ps.setNull(2, Types.TIMESTAMP);
            }
            ps.setString(3, versao.id().toString());
            return ps;
        });
    }

    /** Busca o maior número de versão existente para a chave (definição, data de referência, momento). */
    @Override
    public Optional<Integer> buscarMaiorNumeroVersao(UUID definicaoCurvaId, LocalDate dataReferencia, MomentoCurva momentoCurva) {
        Integer maior = jdbcTemplate.queryForObject(
                """
                SELECT MAX(numero_versao) AS maior FROM versao_curva
                WHERE definicao_curva_id = ? AND data_referencia = ? AND momento_curva = ?
                """,
                Integer.class,
                definicaoCurvaId.toString(),
                dataReferencia,
                momentoCurva.name());

        return Optional.ofNullable(maior);
    }

    /** Busca a versão atualmente PUBLICADA para a chave (definição, data de referência, momento). */
    @Override
    public Optional<VersaoCurva> buscarPublicadaAtual(UUID definicaoCurvaId, LocalDate dataReferencia, MomentoCurva momentoCurva) {
        var linhas = jdbcTemplate.query(
                """
                SELECT id, definicao_curva_id, versao_definicao_curva_id, data_referencia, momento_curva,
                       numero_versao, origem_versao, estado, execucao_curva_id, publicado_em
                FROM versao_curva
                WHERE definicao_curva_id = ? AND data_referencia = ? AND momento_curva = ? AND estado = 'PUBLICADA'
                """,
                (rs, rowNum) -> VersaoCurva.reidratar(
                        UUID.fromString(rs.getString("id")),
                        UUID.fromString(rs.getString("definicao_curva_id")),
                        UUID.fromString(rs.getString("versao_definicao_curva_id")),
                        rs.getObject("data_referencia", LocalDate.class),
                        MomentoCurva.valueOf(rs.getString("momento_curva")),
                        rs.getInt("numero_versao"),
                        OrigemVersao.valueOf(rs.getString("origem_versao")),
                        EstadoVersaoCurva.valueOf(rs.getString("estado")),
                        UUID.fromString(rs.getString("execucao_curva_id")),
                        rs.getTimestamp("publicado_em") != null ? rs.getTimestamp("publicado_em").toInstant() : null
                ),
                definicaoCurvaId.toString(),
                dataReferencia,
                momentoCurva.name());

        return linhas.stream().findFirst();
    }

    /** Busca uma versão de curva pelo id — usado pelo reconhecimento de recarga por hash (tarefa 6.10). */
    @Override
    public Optional<VersaoCurva> buscarPorId(UUID id) {
        var linhas = jdbcTemplate.query(
                """
                SELECT id, definicao_curva_id, versao_definicao_curva_id, data_referencia, momento_curva,
                       numero_versao, origem_versao, estado, execucao_curva_id, publicado_em
                FROM versao_curva
                WHERE id = ?
                """,
                (rs, rowNum) -> VersaoCurva.reidratar(
                        UUID.fromString(rs.getString("id")),
                        UUID.fromString(rs.getString("definicao_curva_id")),
                        UUID.fromString(rs.getString("versao_definicao_curva_id")),
                        rs.getObject("data_referencia", LocalDate.class),
                        MomentoCurva.valueOf(rs.getString("momento_curva")),
                        rs.getInt("numero_versao"),
                        OrigemVersao.valueOf(rs.getString("origem_versao")),
                        EstadoVersaoCurva.valueOf(rs.getString("estado")),
                        UUID.fromString(rs.getString("execucao_curva_id")),
                        rs.getTimestamp("publicado_em") != null ? rs.getTimestamp("publicado_em").toInstant() : null
                ),
                id.toString());

        return linhas.stream().findFirst();
    }
}
