package com.poccurves.processor;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Teste de integração real: exige o ambiente local Podman de pé
 * (`deploy/podman/up.sh --lite`), com o SQL Server já migrado.
 * Nomeado com sufixo "IT" — fora do build padrão (Surefire só coleta
 * `**&#47;*Test.java`).
 * <p>
 * Implementa as tarefas 3.8 e 3.9 do backlog curves-solution-architecture:
 * <ul>
 *   <li>3.8: Conformidade de esquema que rejeita colunas de valor de mercado
 *       em ponto flutuante (float/real/double) e exige DECIMAL(28,12) para
 *       todas as colunas de taxas, fatores, medidas e limites.</li>
 *   <li>3.9: Teste de ida e volta (round-trip) de precisão em DECIMAL(28,12)
 *       garantindo integridade numérica exata em todas as 12 casas decimais.</li>
 * </ul>
 */
class EsquemaPrecisaoDecimalIT {

    private static JdbcTemplate jdbcTemplateSa() {
        DriverManagerDataSource ds = new DriverManagerDataSource(
                "jdbc:sqlserver://localhost:1433;databaseName=curvasdb;trustServerCertificate=true;encrypt=true",
                "sa", "CurvasP0c!Local");
        ds.setDriverClassName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
        return new JdbcTemplate(ds);
    }

    private long semearLoteIngestao(JdbcTemplate sa) {
        String loteExternoId = "it-lote-precisao-" + UUID.randomUUID();
        return sa.queryForObject(
                "INSERT INTO lote_ingestao (fonte, conjunto_dados, tipo_payload, data_referencia, lote_externo_id, id_evento, hash_payload, total_blocos, estado) " +
                        "OUTPUT INSERTED.id " +
                        "VALUES ('B3', 'CURVA_REFERENCIA', 'READY_CURVE', '2026-08-21', ?, 'evt-it', 'hash-it', 1, 'COMPLETO')",
                Long.class, loteExternoId);
    }

    @AfterEach
    void limpar() {
        JdbcTemplate sa = jdbcTemplateSa();
        sa.update("DELETE FROM ponto_dado_mercado WHERE chave_instrumento LIKE 'IT_PRECISAO_%'");
        sa.update("DELETE FROM lote_ingestao WHERE lote_externo_id LIKE 'it-lote-precisao-%'");
    }

    /**
     * Tarefa 3.8: Rejeita colunas de valor de mercado em ponto flutuante (float/real)
     * e garante conformidade de esquema exigindo DECIMAL(28,12).
     */
    @ParameterizedTest(name = "Tabela {0}, Coluna {1} deve ser DECIMAL(28,12) e rejeitar ponto flutuante")
    @CsvSource({
            "ponto_dado_mercado, valor",
            "vertice_curva, taxa",
            "vertice_curva, fator_desconto",
            "validacao_curva, medida_observada",
            "validacao_curva, limite_aplicado"
    })
    void esquemaRejeitaPontoFlutuanteEExigeDecimal2812(String tabela, String coluna) {
        JdbcTemplate sa = jdbcTemplateSa();
        Map<String, Object> col = sa.queryForMap(
                "SELECT DATA_TYPE, NUMERIC_PRECISION, NUMERIC_SCALE " +
                        "FROM INFORMATION_SCHEMA.COLUMNS " +
                        "WHERE TABLE_NAME = ? AND COLUMN_NAME = ?",
                tabela, coluna);

        assertThat(col)
                .as("Coluna %s.%s deve existir no INFORMATION_SCHEMA", tabela, coluna)
                .isNotEmpty();

        assertThat((String) col.get("DATA_TYPE"))
                .as("Coluna %s.%s não pode ser float/real e deve ser 'decimal'", tabela, coluna)
                .isEqualToIgnoringCase("decimal");

        assertThat(((Number) col.get("NUMERIC_PRECISION")).intValue())
                .as("Precisão da coluna %s.%s deve ser 28", tabela, coluna)
                .isEqualTo(28);

        assertThat(((Number) col.get("NUMERIC_SCALE")).intValue())
                .as("Escala da coluna %s.%s deve ser 12", tabela, coluna)
                .isEqualTo(12);
    }

    /**
     * Tarefa 3.9: Ida e volta (round-trip) de precisão em DECIMAL(28,12)
     * comprovando que não há truncamento nem perda de precisão nas 12 casas decimais.
     */
    @Test
    void idaEVoltaPreservaPrecisaoExataEmDecimal2812SemPerdaDeCasasDecimais() {
        JdbcTemplate sa = jdbcTemplateSa();
        long loteId = semearLoteIngestao(sa);

        String chaveInstrumento = "IT_PRECISAO_" + UUID.randomUUID();
        BigDecimal valorOriginal = new BigDecimal("123456789012.123456789012");

        Long idGravado = sa.queryForObject(
                "INSERT INTO ponto_dado_mercado (fonte, conjunto_dados, data_referencia, chave_instrumento, valor, tipo_cotacao, data_vencimento, lote_ingestao_id) " +
                        "OUTPUT INSERTED.id " +
                        "VALUES ('B3', 'CURVA_REFERENCIA', '2026-08-21', ?, ?, 'PU', '2030-01-01', ?)",
                Long.class, chaveInstrumento, valorOriginal, loteId);

        assertThat(idGravado).isNotNull();

        BigDecimal valorLido = sa.queryForObject(
                "SELECT valor FROM ponto_dado_mercado WHERE id = ?",
                BigDecimal.class, idGravado);

        assertThat(valorLido).isNotNull();
        assertThat(valorLido).isEqualByComparingTo(valorOriginal);
        assertThat(valorLido.scale()).isEqualTo(12);
    }
}
