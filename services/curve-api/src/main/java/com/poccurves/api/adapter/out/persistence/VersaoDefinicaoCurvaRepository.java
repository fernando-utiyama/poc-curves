package com.poccurves.api.adapter.out.persistence;

import com.poccurves.api.application.VersaoDefinicaoCurvaRepositoryPort;
import com.poccurves.api.domain.VersaoDefinicaoCurva;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class VersaoDefinicaoCurvaRepository implements VersaoDefinicaoCurvaRepositoryPort {

    private final JdbcTemplate jdbcTemplate;

    public VersaoDefinicaoCurvaRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static final RowMapper<VersaoDefinicaoCurva> ROW_MAPPER = new RowMapper<>() {
        @Override
        public VersaoDefinicaoCurva mapRow(ResultSet rs, int rowNum) throws SQLException {
            UUID id = UUID.fromString(rs.getString("id"));
            UUID definicaoCurvaId = UUID.fromString(rs.getString("definicao_curva_id"));
            int numeroVersao = rs.getInt("numero_versao");
            String contagemDias = rs.getString("contagem_dias");
            String calendario = rs.getString("calendario");
            String interpolador = rs.getString("interpolador");
            String politicaExtrapolacao = rs.getString("politica_extrapolacao");
            String politicaArredondamento = rs.getString("politica_arredondamento");

            String modeloCurvaIdStr = rs.getString("modelo_curva_id");
            UUID modeloCurvaId = modeloCurvaIdStr != null ? UUID.fromString(modeloCurvaIdStr) : null;

            Integer orcIngestao = (Integer) rs.getObject("orcamento_ingestao_segundos");
            Integer orcConstrucao = (Integer) rs.getObject("orcamento_construcao_segundos");
            Integer orcValidacao = (Integer) rs.getObject("orcamento_validacao_segundos");
            Integer orcPublicacao = (Integer) rs.getObject("orcamento_publicacao_segundos");
            Integer janelaBloqueio = (Integer) rs.getObject("janela_bloqueio_minutos");

            String vinculosFonte = rs.getString("vinculos_fonte");
            String dependeDe = rs.getString("depende_de");
            String limitesValidacao = rs.getString("limites_validacao");

            Date vigInicioDate = rs.getDate("vigencia_inicio");
            LocalDate vigenciaInicio = vigInicioDate != null ? vigInicioDate.toLocalDate() : null;

            Date vigFimDate = rs.getDate("vigencia_fim");
            LocalDate vigenciaFim = vigFimDate != null ? vigFimDate.toLocalDate() : null;

            return new VersaoDefinicaoCurva(
                    id, definicaoCurvaId, numeroVersao, contagemDias, calendario, interpolador,
                    politicaExtrapolacao, politicaArredondamento, modeloCurvaId, orcIngestao,
                    orcConstrucao, orcValidacao, orcPublicacao, janelaBloqueio, vinculosFonte,
                    dependeDe, limitesValidacao, vigenciaInicio, vigenciaFim
            );
        }
    };

    @Override
    public void salvar(VersaoDefinicaoCurva v) {
        jdbcTemplate.update(
                """
                INSERT INTO versao_definicao_curva (
                    id, definicao_curva_id, numero_versao, contagem_dias, calendario,
                    interpolador, politica_extrapolacao, politica_arredondamento, modelo_curva_id,
                    orcamento_ingestao_segundos, orcamento_construcao_segundos, orcamento_validacao_segundos,
                    orcamento_publicacao_segundos, janela_bloqueio_minutos, vinculos_fonte,
                    depende_de, limites_validacao, vigencia_inicio, vigencia_fim
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                v.id().toString(),
                v.definicaoCurvaId().toString(),
                v.numeroVersao(),
                v.contagemDias(),
                v.calendario(),
                v.interpolador(),
                v.politicaExtrapolacao(),
                v.politicaArredondamento(),
                v.modeloCurvaId() != null ? v.modeloCurvaId().toString() : null,
                v.orcamentoIngestaoSegundos(),
                v.orcamentoConstrucaoSegundos(),
                v.orcamentoValidacaoSegundos(),
                v.orcamentoPublicacaoSegundos(),
                v.janelaBloqueioMinutos(),
                v.vinculosFonteJson(),
                v.dependeDeJson(),
                v.limitesValidacaoJson(),
                Date.valueOf(v.vigenciaInicio()),
                v.vigenciaFim() != null ? Date.valueOf(v.vigenciaFim()) : null
        );
    }

    @Override
    public void encerrarVigencia(UUID id, LocalDate vigenciaFim) {
        jdbcTemplate.update(
                "UPDATE versao_definicao_curva SET vigencia_fim = ? WHERE id = ?",
                Date.valueOf(vigenciaFim),
                id.toString()
        );
    }

    @Override
    public Optional<VersaoDefinicaoCurva> buscarVigente(UUID definicaoCurvaId, LocalDate data) {
        try {
            VersaoDefinicaoCurva v = jdbcTemplate.queryForObject(
                    """
                    SELECT id, definicao_curva_id, numero_versao, contagem_dias, calendario,
                           interpolador, politica_extrapolacao, politica_arredondamento, modelo_curva_id,
                           orcamento_ingestao_segundos, orcamento_construcao_segundos, orcamento_validacao_segundos,
                           orcamento_publicacao_segundos, janela_bloqueio_minutos, vinculos_fonte,
                           depende_de, limites_validacao, vigencia_inicio, vigencia_fim
                    FROM versao_definicao_curva
                    WHERE definicao_curva_id = ?
                      AND vigencia_inicio <= ?
                      AND (vigencia_fim IS NULL OR vigencia_fim > ?)
                    ORDER BY numero_versao DESC
                    OFFSET 0 ROWS FETCH NEXT 1 ROWS ONLY
                    """,
                    ROW_MAPPER,
                    definicaoCurvaId.toString(),
                    Date.valueOf(data),
                    Date.valueOf(data)
            );
            return Optional.ofNullable(v);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<VersaoDefinicaoCurva> buscarMaisRecente(UUID definicaoCurvaId) {
        try {
            VersaoDefinicaoCurva v = jdbcTemplate.queryForObject(
                    """
                    SELECT id, definicao_curva_id, numero_versao, contagem_dias, calendario,
                           interpolador, politica_extrapolacao, politica_arredondamento, modelo_curva_id,
                           orcamento_ingestao_segundos, orcamento_construcao_segundos, orcamento_validacao_segundos,
                           orcamento_publicacao_segundos, janela_bloqueio_minutos, vinculos_fonte,
                           depende_de, limites_validacao, vigencia_inicio, vigencia_fim
                    FROM versao_definicao_curva
                    WHERE definicao_curva_id = ?
                    ORDER BY numero_versao DESC
                    OFFSET 0 ROWS FETCH NEXT 1 ROWS ONLY
                    """,
                    ROW_MAPPER,
                    definicaoCurvaId.toString()
            );
            return Optional.ofNullable(v);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<VersaoDefinicaoCurva> buscarPorNumero(UUID definicaoCurvaId, int numeroVersao) {
        try {
            VersaoDefinicaoCurva v = jdbcTemplate.queryForObject(
                    """
                    SELECT id, definicao_curva_id, numero_versao, contagem_dias, calendario,
                           interpolador, politica_extrapolacao, politica_arredondamento, modelo_curva_id,
                           orcamento_ingestao_segundos, orcamento_construcao_segundos, orcamento_validacao_segundos,
                           orcamento_publicacao_segundos, janela_bloqueio_minutos, vinculos_fonte,
                           depende_de, limites_validacao, vigencia_inicio, vigencia_fim
                    FROM versao_definicao_curva
                    WHERE definicao_curva_id = ? AND numero_versao = ?
                    """,
                    ROW_MAPPER,
                    definicaoCurvaId.toString(),
                    numeroVersao
            );
            return Optional.ofNullable(v);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    @Override
    public List<VersaoDefinicaoCurva> listarPorDefinicao(UUID definicaoCurvaId) {
        return jdbcTemplate.query(
                """
                SELECT id, definicao_curva_id, numero_versao, contagem_dias, calendario,
                       interpolador, politica_extrapolacao, politica_arredondamento, modelo_curva_id,
                       orcamento_ingestao_segundos, orcamento_construcao_segundos, orcamento_validacao_segundos,
                       orcamento_publicacao_segundos, janela_bloqueio_minutos, vinculos_fonte,
                       depende_de, limites_validacao, vigencia_inicio, vigencia_fim
                FROM versao_definicao_curva
                WHERE definicao_curva_id = ?
                ORDER BY numero_versao DESC
                """,
                ROW_MAPPER,
                definicaoCurvaId.toString()
        );
    }
}
