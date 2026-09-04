package com.poccurves.processor.adapter.out.persistence;

import com.poccurves.processor.application.PontoDadoMercadoRepositoryPort;
import com.poccurves.processor.domain.DivergenciaValor;
import com.poccurves.processor.domain.PontoDadoMercado;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Persistência JDBC de {@link PontoDadoMercado} — upsert pela chave natural
 * (fonte, conjunto_dados, data_referencia, chave_instrumento), espelhando
 * db/migration/V2__dado_mercado.sql (tabela ponto_dado_mercado).
 */
@Repository
public class PontoDadoMercadoRepository implements PontoDadoMercadoRepositoryPort {

    private final JdbcTemplate jdbcTemplate;

    public PontoDadoMercadoRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Lê de volta todos os pontos gravados sob um lote — usado para reconstruir a lista
     * completa de vértices de um lote READY_CURVE já persistido (em vez de acumular em
     * memória entre chamadas de bloco, o que não sobreviveria a múltiplas mensagens Kafka),
     * na hora de publicar a versão IMPORTADA correspondente.
     */
    @Override
    public List<PontoDadoMercado> buscarPorLoteIngestaoId(long loteIngestaoId) {
        return jdbcTemplate.query(
                """
                SELECT fonte, conjunto_dados, data_referencia, chave_instrumento, valor, tipo_cotacao, data_vencimento
                FROM ponto_dado_mercado
                WHERE lote_ingestao_id = ?
                """,
                (rs, rowNum) -> new PontoDadoMercado(
                        rs.getString("fonte"),
                        rs.getString("conjunto_dados"),
                        rs.getDate("data_referencia").toLocalDate(),
                        rs.getString("chave_instrumento"),
                        rs.getBigDecimal("valor"),
                        rs.getString("tipo_cotacao"),
                        rs.getDate("data_vencimento") != null ? rs.getDate("data_vencimento").toLocalDate() : null),
                loteIngestaoId);
    }

    private Optional<BigDecimal> buscarValorAtual(PontoDadoMercado ponto) {
        var valores = jdbcTemplate.query(
                """
                SELECT valor FROM ponto_dado_mercado
                WHERE fonte = ? AND conjunto_dados = ? AND data_referencia = ? AND chave_instrumento = ?
                """,
                (rs, rowNum) -> rs.getBigDecimal("valor"),
                ponto.fonte(), ponto.conjuntoDados(), ponto.dataReferencia(), ponto.chaveInstrumento());
        return valores.stream().findFirst();
    }

    /**
     * Upsert de um ponto: INSERT se a chave natural não existir, UPDATE se
     * existir. Retorna a divergência detectada (valor antigo != valor novo),
     * vazio se era um INSERT novo ou se o valor regravado é numericamente
     * igual ao anterior.
     */
    @Override
    public Optional<DivergenciaValor> upsert(PontoDadoMercado ponto, long loteIngestaoId) {
        Optional<BigDecimal> valorAnterior = buscarValorAtual(ponto);

        if (valorAnterior.isEmpty()) {
            jdbcTemplate.update(
                    """
                    INSERT INTO ponto_dado_mercado
                        (fonte, conjunto_dados, data_referencia, chave_instrumento, valor,
                         tipo_cotacao, data_vencimento, lote_ingestao_id, atualizado_em)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, SYSUTCDATETIME())
                    """,
                    ponto.fonte(), ponto.conjuntoDados(), ponto.dataReferencia(), ponto.chaveInstrumento(),
                    ponto.valor(), ponto.tipoCotacao(), ponto.dataVencimento(), loteIngestaoId);
            return Optional.empty();
        }

        Optional<DivergenciaValor> divergencia = DivergenciaValor.detectar(
                ponto.chaveInstrumento(), valorAnterior.get(), ponto.valor());

        jdbcTemplate.update(
                """
                UPDATE ponto_dado_mercado
                SET valor = ?, tipo_cotacao = ?, data_vencimento = ?, lote_ingestao_id = ?, atualizado_em = SYSUTCDATETIME()
                WHERE fonte = ? AND conjunto_dados = ? AND data_referencia = ? AND chave_instrumento = ?
                """,
                ponto.valor(), ponto.tipoCotacao(), ponto.dataVencimento(), loteIngestaoId,
                ponto.fonte(), ponto.conjuntoDados(), ponto.dataReferencia(), ponto.chaveInstrumento());

        return divergencia;
    }
}
