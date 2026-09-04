package com.poccurves.processor.adapter.out.persistence;
import com.poccurves.processor.domain.ingestao.EstadoLoteIngestao;
import com.poccurves.processor.domain.ingestao.LoteIngestao;
import com.poccurves.processor.domain.ingestao.LoteJaExisteException;
import com.poccurves.processor.domain.parsing.TipoPayload;

import com.poccurves.processor.application.LoteIngestaoRepositoryPort;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/** Persistência JDBC de {@link LoteIngestao} — espelha db/migration/V2__dado_mercado.sql (tabela lote_ingestao). */
@Repository
public class LoteIngestaoRepository implements LoteIngestaoRepositoryPort {

    private final JdbcTemplate jdbcTemplate;

    public LoteIngestaoRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Busca um lote existente pelo identificador externo determinístico (loteId do envelope). */
    @Override
    public Optional<LoteIngestao> buscarPorLoteExternoId(String loteExternoId) {
        var linhas = jdbcTemplate.query(
                """
                SELECT id, execucao_curva_id, fonte, conjunto_dados, tipo_payload, data_referencia,
                       lote_externo_id, correlation_id, id_evento, hash_payload, total_blocos, blocos_recebidos,
                       pontos_recebidos, pontos_gravados, pontos_divergentes, divergencias, estado, recebido_em
                FROM lote_ingestao
                WHERE lote_externo_id = ?
                """,
                (rs, rowNum) -> LoteIngestao.reidratar(
                        rs.getLong("id"),
                        rs.getObject("execucao_curva_id") != null ? UUID.fromString(rs.getString("execucao_curva_id")) : null,
                        rs.getString("fonte"),
                        rs.getString("conjunto_dados"),
                        TipoPayload.valueOf(rs.getString("tipo_payload")),
                        rs.getObject("data_referencia", LocalDate.class),
                        rs.getString("lote_externo_id"),
                        rs.getString("correlation_id") != null ? UUID.fromString(rs.getString("correlation_id")) : null,
                        rs.getString("id_evento"),
                        rs.getString("hash_payload"),
                        rs.getInt("total_blocos"),
                        rs.getInt("blocos_recebidos"),
                        rs.getInt("pontos_recebidos"),
                        rs.getInt("pontos_gravados"),
                        rs.getInt("pontos_divergentes"),
                        rs.getString("divergencias"),
                        EstadoLoteIngestao.valueOf(rs.getString("estado")),
                        rs.getTimestamp("recebido_em").toInstant()
                ),
                loteExternoId);

        return linhas.stream().findFirst();
    }

    /**
     * Insere um lote novo (recém-aberto via {@link LoteIngestao#abrir}) e atribui o id gerado pelo banco.
     *
     * @throws LoteJaExisteException se a restrição UNIQUE de lote_externo_id for violada — tradução de
     *                                {@link DataIntegrityViolationException}, para que quem chama (application)
     *                                não precise depender de tipo de framework de persistência.
     */
    @Override
    public void inserir(LoteIngestao lote) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        try {
            jdbcTemplate.update(connection -> {
                PreparedStatement ps = connection.prepareStatement(
                        """
                        INSERT INTO lote_ingestao
                            (execucao_curva_id, fonte, conjunto_dados, tipo_payload, data_referencia,
                             lote_externo_id, correlation_id, id_evento, hash_payload, total_blocos, blocos_recebidos,
                             pontos_recebidos, pontos_gravados, pontos_divergentes, divergencias, estado, recebido_em)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                        Statement.RETURN_GENERATED_KEYS);
                if (lote.execucaoCurvaId() != null) {
                    ps.setString(1, lote.execucaoCurvaId().toString());
                } else {
                    ps.setNull(1, Types.VARCHAR);
                }
                ps.setString(2, lote.fonte());
                ps.setString(3, lote.conjuntoDados());
                ps.setString(4, lote.tipoPayload().name());
                ps.setObject(5, lote.dataReferencia());
                ps.setString(6, lote.loteExternoId());
                if (lote.correlationId() != null) {
                    ps.setString(7, lote.correlationId().toString());
                } else {
                    ps.setNull(7, Types.VARCHAR);
                }
                ps.setString(8, lote.idEvento());
                ps.setString(9, lote.hashPayload());
                ps.setInt(10, lote.totalBlocos());
                ps.setInt(11, lote.blocosRecebidos());
                ps.setInt(12, lote.pontosRecebidos());
                ps.setInt(13, lote.pontosGravados());
                ps.setInt(14, lote.pontosDivergentes());
                ps.setString(15, lote.divergencias());
                ps.setString(16, lote.estado().name());
                ps.setObject(17, java.sql.Timestamp.from(lote.recebidoEm()));
                return ps;
            }, keyHolder);
        } catch (DataIntegrityViolationException e) {
            throw new LoteJaExisteException(lote.loteExternoId(), e);
        }

        lote.atribuirId(keyHolder.getKey().longValue());
    }

    /** Grava o estado atual do lote (contadores, estado, divergências, idEvento do último bloco). */
    @Override
    public void atualizar(LoteIngestao lote) {
        jdbcTemplate.update(
                """
                UPDATE lote_ingestao
                SET id_evento = ?, blocos_recebidos = ?, pontos_recebidos = ?, pontos_gravados = ?,
                    pontos_divergentes = ?, divergencias = ?, estado = ?
                WHERE id = ?
                """,
                lote.idEvento(),
                lote.blocosRecebidos(),
                lote.pontosRecebidos(),
                lote.pontosGravados(),
                lote.pontosDivergentes(),
                lote.divergencias(),
                lote.estado().name(),
                lote.id());
    }
}
