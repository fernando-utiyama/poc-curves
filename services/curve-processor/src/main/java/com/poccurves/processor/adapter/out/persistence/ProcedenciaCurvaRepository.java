package com.poccurves.processor.adapter.out.persistence;

import com.poccurves.processor.application.ProcedenciaCurvaRepositoryPort;
import com.poccurves.processor.domain.ProcedenciaCurva;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistência JDBC de {@link ProcedenciaCurva} — espelha db/migration/V4__versao_curva.sql
 * (tabela procedencia_curva).
 */
@Repository
public class ProcedenciaCurvaRepository implements ProcedenciaCurvaRepositoryPort {

    private final JdbcTemplate jdbcTemplate;

    public ProcedenciaCurvaRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Reconhece recarga do mesmo arquivo pela hash (tarefa 6.10 do backlog):
     * o id da versao_curva já publicada com este hash_arquivo, se houver.
     */
    @Override
    public Optional<UUID> buscarVersaoCurvaIdPorHashArquivo(String hashArquivo) {
        var linhas = jdbcTemplate.query(
                "SELECT versao_curva_id FROM procedencia_curva WHERE hash_arquivo = ?",
                (rs, rowNum) -> UUID.fromString(rs.getString("versao_curva_id")),
                hashArquivo);
        return linhas.stream().findFirst();
    }

    /**
     * Insere o registro de procedência de uma versão de curva.
     */
    @Override
    public void inserir(UUID versaoCurvaId, ProcedenciaCurva procedencia, int numeroVersaoDefinicao) {
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    """
                    INSERT INTO procedencia_curva
                        (id, versao_curva_id, execucao_curva_id, numero_versao_definicao, checksum_modelo,
                         referencias_insumo, hash_conjunto_insumos, lote_ingestao_id, arquivo_carga,
                         hash_arquivo, carregado_por, justificativa, versao_motor, criado_em)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """);
            ps.setString(1, UUID.randomUUID().toString());
            ps.setString(2, versaoCurvaId.toString());
            ps.setString(3, procedencia.execucaoCurvaId().toString());
            ps.setInt(4, numeroVersaoDefinicao);
            ps.setNull(5, Types.NVARCHAR);
            ps.setString(6, procedencia.referenciasInsumo());
            ps.setString(7, procedencia.hashConjuntoInsumos());
            if (procedencia.loteIngestaoId() != null) {
                ps.setLong(8, procedencia.loteIngestaoId());
            } else {
                ps.setNull(8, Types.BIGINT);
            }
            ps.setString(9, procedencia.arquivoCarga());
            ps.setString(10, procedencia.hashArquivo());
            ps.setString(11, procedencia.carregadoPor());
            ps.setString(12, procedencia.justificativa());
            ps.setNull(13, Types.NVARCHAR);
            ps.setObject(14, Timestamp.from(Instant.now()));
            return ps;
        });
    }
}
