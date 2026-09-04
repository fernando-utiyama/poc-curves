package com.poccurves.api.adapter.out.persistence;

import com.poccurves.api.application.CurvaConsultaRepositoryPort;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class CurvaConsultaRepository implements CurvaConsultaRepositoryPort {

    private final JdbcTemplate jdbcTemplate;

    public CurvaConsultaRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<VersaoCurvaRegistro> buscarVersaoPublicada(String codigoCurva, LocalDate dataReferencia, String momentoCurva) {
        try {
            var reg = jdbcTemplate.queryForObject(
                    """
                    SELECT vc.id AS versao_curva_id, dc.id AS definicao_curva_id, dc.codigo AS codigo_curva,
                           dc.nome AS nome_curva, dc.modo_origem, vc.data_referencia, vc.momento_curva,
                           vc.numero_versao, vc.origem_versao, vc.estado, vc.execucao_curva_id, vc.publicado_em
                    FROM versao_curva vc
                    INNER JOIN definicao_curva dc ON dc.id = vc.definicao_curva_id
                    WHERE dc.codigo = ? AND vc.data_referencia = ? AND vc.momento_curva = ?
                      AND vc.estado = 'PUBLICADA'
                    """,
                    (rs, rowNum) -> new VersaoCurvaRegistro(
                            UUID.fromString(rs.getString("versao_curva_id")),
                            UUID.fromString(rs.getString("definicao_curva_id")),
                            rs.getString("codigo_curva"),
                            rs.getString("nome_curva"),
                            rs.getString("modo_origem"),
                            rs.getDate("data_referencia").toLocalDate(),
                            rs.getString("momento_curva"),
                            rs.getInt("numero_versao"),
                            rs.getString("origem_versao"),
                            rs.getString("estado"),
                            UUID.fromString(rs.getString("execucao_curva_id")),
                            rs.getTimestamp("publicado_em") != null ? rs.getTimestamp("publicado_em").toInstant() : null
                    ),
                    codigoCurva,
                    Date.valueOf(dataReferencia),
                    momentoCurva
            );
            return Optional.ofNullable(reg);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<VersaoCurvaRegistro> buscarVersaoPorNumero(String codigoCurva, LocalDate dataReferencia, String momentoCurva, int numeroVersao) {
        try {
            var reg = jdbcTemplate.queryForObject(
                    """
                    SELECT vc.id AS versao_curva_id, dc.id AS definicao_curva_id, dc.codigo AS codigo_curva,
                           dc.nome AS nome_curva, dc.modo_origem, vc.data_referencia, vc.momento_curva,
                           vc.numero_versao, vc.origem_versao, vc.estado, vc.execucao_curva_id, vc.publicado_em
                    FROM versao_curva vc
                    INNER JOIN definicao_curva dc ON dc.id = vc.definicao_curva_id
                    WHERE dc.codigo = ? AND vc.data_referencia = ? AND vc.momento_curva = ?
                      AND vc.numero_versao = ?
                    """,
                    (rs, rowNum) -> new VersaoCurvaRegistro(
                            UUID.fromString(rs.getString("versao_curva_id")),
                            UUID.fromString(rs.getString("definicao_curva_id")),
                            rs.getString("codigo_curva"),
                            rs.getString("nome_curva"),
                            rs.getString("modo_origem"),
                            rs.getDate("data_referencia").toLocalDate(),
                            rs.getString("momento_curva"),
                            rs.getInt("numero_versao"),
                            rs.getString("origem_versao"),
                            rs.getString("estado"),
                            UUID.fromString(rs.getString("execucao_curva_id")),
                            rs.getTimestamp("publicado_em") != null ? rs.getTimestamp("publicado_em").toInstant() : null
                    ),
                    codigoCurva,
                    Date.valueOf(dataReferencia),
                    momentoCurva,
                    numeroVersao
            );
            return Optional.ofNullable(reg);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<VersaoCurvaRegistro> buscarVersaoAsOf(String codigoCurva, LocalDate dataReferencia, String momentoCurva, Instant asOf) {
        try {
            var reg = jdbcTemplate.queryForObject(
                    """
                    SELECT vc.id AS versao_curva_id, dc.id AS definicao_curva_id, dc.codigo AS codigo_curva,
                           dc.nome AS nome_curva, dc.modo_origem, vc.data_referencia, vc.momento_curva,
                           vc.numero_versao, vc.origem_versao, vc.estado, vc.execucao_curva_id, vc.publicado_em
                    FROM versao_curva vc
                    INNER JOIN definicao_curva dc ON dc.id = vc.definicao_curva_id
                    WHERE dc.codigo = ? AND vc.data_referencia = ? AND vc.momento_curva = ?
                      AND vc.publicado_em <= ?
                    ORDER BY vc.publicado_em DESC, vc.numero_versao DESC
                    OFFSET 0 ROWS FETCH NEXT 1 ROWS ONLY
                    """,
                    (rs, rowNum) -> new VersaoCurvaRegistro(
                            UUID.fromString(rs.getString("versao_curva_id")),
                            UUID.fromString(rs.getString("definicao_curva_id")),
                            rs.getString("codigo_curva"),
                            rs.getString("nome_curva"),
                            rs.getString("modo_origem"),
                            rs.getDate("data_referencia").toLocalDate(),
                            rs.getString("momento_curva"),
                            rs.getInt("numero_versao"),
                            rs.getString("origem_versao"),
                            rs.getString("estado"),
                            UUID.fromString(rs.getString("execucao_curva_id")),
                            rs.getTimestamp("publicado_em") != null ? rs.getTimestamp("publicado_em").toInstant() : null
                    ),
                    codigoCurva,
                    Date.valueOf(dataReferencia),
                    momentoCurva,
                    Timestamp.from(asOf)
            );
            return Optional.ofNullable(reg);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    @Override
    public List<VerticeRegistro> listarVertices(UUID versaoCurvaId, int offset, int limit) {
        return jdbcTemplate.query(
                """
                SELECT prazo_dias_uteis, prazo_dias_corridos, data_vencimento, taxa, fator_desconto
                FROM vertice_curva
                WHERE versao_curva_id = ?
                ORDER BY prazo_dias_uteis ASC
                OFFSET ? ROWS FETCH NEXT ? ROWS ONLY
                """,
                (rs, rowNum) -> new VerticeRegistro(
                        rs.getInt("prazo_dias_uteis"),
                        (Integer) rs.getObject("prazo_dias_corridos"),
                        rs.getDate("data_vencimento") != null ? rs.getDate("data_vencimento").toLocalDate() : null,
                        rs.getBigDecimal("taxa"),
                        rs.getBigDecimal("fator_desconto")
                ),
                versaoCurvaId.toString(),
                offset,
                limit
        );
    }

    @Override
    public int contarVertices(UUID versaoCurvaId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM vertice_curva WHERE versao_curva_id = ?",
                Integer.class,
                versaoCurvaId.toString()
        );
        return count != null ? count : 0;
    }

    @Override
    public List<VerticeRegistro> buscarTodosVertices(UUID versaoCurvaId) {
        return jdbcTemplate.query(
                """
                SELECT prazo_dias_uteis, prazo_dias_corridos, data_vencimento, taxa, fator_desconto
                FROM vertice_curva
                WHERE versao_curva_id = ?
                ORDER BY prazo_dias_uteis ASC
                """,
                (rs, rowNum) -> new VerticeRegistro(
                        rs.getInt("prazo_dias_uteis"),
                        (Integer) rs.getObject("prazo_dias_corridos"),
                        rs.getDate("data_vencimento") != null ? rs.getDate("data_vencimento").toLocalDate() : null,
                        rs.getBigDecimal("taxa"),
                        rs.getBigDecimal("fator_desconto")
                ),
                versaoCurvaId.toString()
        );
    }

    @Override
    public List<VersaoCurvaRegistro> listarHistoricoVersoes(String codigoCurva, LocalDate dataReferencia, String momentoCurva) {
        return jdbcTemplate.query(
                """
                SELECT vc.id AS versao_curva_id, dc.id AS definicao_curva_id, dc.codigo AS codigo_curva,
                       dc.nome AS nome_curva, dc.modo_origem, vc.data_referencia, vc.momento_curva,
                       vc.numero_versao, vc.origem_versao, vc.estado, vc.execucao_curva_id, vc.publicado_em
                FROM versao_curva vc
                INNER JOIN definicao_curva dc ON dc.id = vc.definicao_curva_id
                WHERE dc.codigo = ? AND vc.data_referencia = ? AND vc.momento_curva = ?
                ORDER BY vc.numero_versao DESC
                """,
                (rs, rowNum) -> new VersaoCurvaRegistro(
                        UUID.fromString(rs.getString("versao_curva_id")),
                        UUID.fromString(rs.getString("definicao_curva_id")),
                        rs.getString("codigo_curva"),
                        rs.getString("nome_curva"),
                        rs.getString("modo_origem"),
                        rs.getDate("data_referencia").toLocalDate(),
                        rs.getString("momento_curva"),
                        rs.getInt("numero_versao"),
                        rs.getString("origem_versao"),
                        rs.getString("estado"),
                        UUID.fromString(rs.getString("execucao_curva_id")),
                        rs.getTimestamp("publicado_em") != null ? rs.getTimestamp("publicado_em").toInstant() : null
                ),
                codigoCurva,
                Date.valueOf(dataReferencia),
                momentoCurva
        );
    }

    @Override
    public Optional<ProcedenciaRegistro> buscarProcedencia(UUID versaoCurvaId) {
        try {
            var reg = jdbcTemplate.queryForObject(
                    """
                    SELECT pc.id, pc.versao_curva_id, pc.execucao_curva_id, ec.correlation_id,
                           pc.numero_versao_definicao, mc.codigo AS modelo_codigo, pc.checksum_modelo,
                           pc.referencias_insumo, pc.hash_conjunto_insumos, pc.lote_ingestao_id,
                           pc.arquivo_carga, pc.hash_arquivo, pc.carregado_por, pc.justificativa,
                           pc.versao_motor, pc.criado_em
                    FROM procedencia_curva pc
                    LEFT JOIN execucao_curva ec ON ec.id = pc.execucao_curva_id
                    LEFT JOIN modelo_curva mc ON mc.id = pc.modelo_curva_id
                    WHERE pc.versao_curva_id = ?
                    """,
                    (rs, rowNum) -> new ProcedenciaRegistro(
                            UUID.fromString(rs.getString("id")),
                            UUID.fromString(rs.getString("versao_curva_id")),
                            UUID.fromString(rs.getString("execucao_curva_id")),
                            rs.getString("correlation_id"),
                            rs.getInt("numero_versao_definicao"),
                            rs.getString("modelo_codigo"),
                            rs.getString("checksum_modelo"),
                            rs.getString("referencias_insumo"),
                            rs.getString("hash_conjunto_insumos"),
                            (Long) rs.getObject("lote_ingestao_id"),
                            rs.getString("arquivo_carga"),
                            rs.getString("hash_arquivo"),
                            rs.getString("carregado_por"),
                            rs.getString("justificativa"),
                            rs.getString("versao_motor"),
                            rs.getTimestamp("criado_em").toInstant()
                    ),
                    versaoCurvaId.toString()
            );
            return Optional.ofNullable(reg);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    @Override
    public List<ValidacaoItemRegistro> buscarValidacoes(UUID versaoCurvaId) {
        return jdbcTemplate.query(
                """
                SELECT teste, classificacao, resultado, medida_observada, limite_aplicado, detalhe, executado_em
                FROM validacao_curva
                WHERE versao_curva_id = ?
                ORDER BY id ASC
                """,
                (rs, rowNum) -> new ValidacaoItemRegistro(
                        rs.getString("teste"),
                        rs.getString("classificacao"),
                        rs.getString("resultado"),
                        rs.getBigDecimal("medida_observada"),
                        rs.getBigDecimal("limite_aplicado"),
                        rs.getString("detalhe"),
                        rs.getTimestamp("executado_em").toInstant()
                ),
                versaoCurvaId.toString()
        );
    }
}
