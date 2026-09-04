package com.poccurves.engine.adapter.out.persistence;
import com.poccurves.engine.domain.versao.ProcedenciaCurva;

import com.poccurves.engine.application.ProcedenciaCurvaRepositoryPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistência JDBC de {@link ProcedenciaCurva} na tabela procedencia_curva
 * (db/migration/V4__versao_curva.sql e V5__modelo_curva.sql).
 */
@Repository
public class ProcedenciaCurvaRepository implements ProcedenciaCurvaRepositoryPort {

    private final JdbcTemplate jdbcTemplate;

    public ProcedenciaCurvaRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<ProcedenciaCurva> mapper = (rs, rowNum) -> {
        long loteIngestaoIdValor = rs.getLong("lote_ingestao_id");
        Long loteIngestaoId = rs.wasNull() ? null : loteIngestaoIdValor;
        String modeloCurvaIdStr = rs.getString("modelo_curva_id");
        UUID modeloCurvaId = modeloCurvaIdStr != null ? UUID.fromString(modeloCurvaIdStr) : null;

        return new ProcedenciaCurva(
                UUID.fromString(rs.getString("versao_curva_id")),
                UUID.fromString(rs.getString("execucao_curva_id")),
                rs.getInt("numero_versao_definicao"),
                rs.getString("checksum_modelo"),
                rs.getString("referencias_insumo"),
                rs.getString("hash_conjunto_insumos"),
                loteIngestaoId,
                rs.getString("arquivo_carga"),
                rs.getString("hash_arquivo"),
                rs.getString("carregado_por"),
                rs.getString("justificativa"),
                rs.getString("versao_motor"),
                modeloCurvaId
        );
    };

    @Override
    public void inserir(ProcedenciaCurva procedencia) {
        String sql = """
                INSERT INTO procedencia_curva (
                    versao_curva_id, execucao_curva_id, numero_versao_definicao, checksum_modelo,
                    referencias_insumo, hash_conjunto_insumos, lote_ingestao_id, arquivo_carga,
                    hash_arquivo, carregado_por, justificativa, versao_motor, modelo_curva_id
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        jdbcTemplate.update(sql,
                procedencia.versaoCurvaId(),
                procedencia.execucaoCurvaId(),
                procedencia.numeroVersaoDefinicao(),
                procedencia.checksumModelo(),
                procedencia.referenciasInsumo(),
                procedencia.hashConjuntoInsumos(),
                procedencia.loteIngestaoId(),
                procedencia.arquivoCarga(),
                procedencia.hashArquivo(),
                procedencia.carregadoPor(),
                procedencia.justificativa(),
                procedencia.versaoMotor(),
                procedencia.modeloCurvaId()
        );
    }

    @Override
    public Optional<ProcedenciaCurva> buscarPorVersaoCurva(UUID versaoCurvaId) {
        String sql = """
                SELECT versao_curva_id, execucao_curva_id, numero_versao_definicao, checksum_modelo,
                       referencias_insumo, hash_conjunto_insumos, lote_ingestao_id, arquivo_carga,
                       hash_arquivo, carregado_por, justificativa, versao_motor, modelo_curva_id
                FROM procedencia_curva
                WHERE versao_curva_id = ?
                """;
        List<ProcedenciaCurva> resultados = jdbcTemplate.query(sql, mapper, versaoCurvaId.toString());
        return resultados.isEmpty() ? Optional.empty() : Optional.of(resultados.get(0));
    }
}
