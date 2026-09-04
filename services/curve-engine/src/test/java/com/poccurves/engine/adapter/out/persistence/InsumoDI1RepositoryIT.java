package com.poccurves.engine.adapter.out.persistence;
import com.poccurves.engine.application.model.CurvaJuros;
import com.poccurves.engine.application.model.InsumoDI1;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class InsumoDI1RepositoryIT {

    private static JdbcTemplate jdbcTemplateApp() {
        DriverManagerDataSource ds = new DriverManagerDataSource(
                "jdbc:sqlserver://localhost:1433;databaseName=curvasdb;trustServerCertificate=true;encrypt=true",
                "curve_engine_app", "CurveEngineP0c!Local");
        ds.setDriverClassName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
        return new JdbcTemplate(ds);
    }

    private static JdbcTemplate jdbcTemplateSa() {
        DriverManagerDataSource ds = new DriverManagerDataSource(
                "jdbc:sqlserver://localhost:1433;databaseName=curvasdb;trustServerCertificate=true;encrypt=true",
                "sa", "CurvasP0c!Local");
        ds.setDriverClassName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
        return new JdbcTemplate(ds);
    }

    private final InsumoDI1Repository repository = new InsumoDI1Repository(jdbcTemplateApp());
    private Long loteId;

    @AfterEach
    void limpar() {
        if (loteId != null) {
            JdbcTemplate sa = jdbcTemplateSa();
            sa.update("DELETE FROM ponto_dado_mercado WHERE lote_ingestao_id = ?", loteId);
            sa.update("DELETE FROM lote_ingestao WHERE id = ?", loteId);
        }
    }

    @Test
    void buscarInsumosDI1() {
        JdbcTemplate sa = jdbcTemplateSa();
        LocalDate dataRef = LocalDate.of(2023, 1, 1);
        
        sa.update("""
                INSERT INTO lote_ingestao (fonte, conjunto_dados, tipo_payload, data_referencia, lote_externo_id, id_evento, hash_payload, total_blocos, estado)
                VALUES ('B3', 'BVBG.086', 'INDIVIDUAL_QUOTES', ?, 'lote-123', 'evt-123', 'hash', 1, 'COMPLETO')
                """, Date.valueOf(dataRef));
        
        loteId = sa.queryForObject("SELECT MAX(id) FROM lote_ingestao", Long.class);

        // Completo
        sa.update("""
                INSERT INTO ponto_dado_mercado (fonte, conjunto_dados, data_referencia, chave_instrumento, valor, tipo_cotacao, lote_ingestao_id)
                VALUES ('B3', 'BVBG.086', ?, 'DI1F24', 12.5, 'TAXA_AJUSTE', ?)
                """, Date.valueOf(dataRef), loteId);
                
        sa.update("""
                INSERT INTO ponto_dado_mercado (fonte, conjunto_dados, data_referencia, chave_instrumento, valor, tipo_cotacao, data_vencimento, lote_ingestao_id)
                VALUES ('B3', 'BVBG.028', ?, 'DI1F24', 252, 'DIAS_UTEIS_VENCIMENTO', ?, ?)
                """, Date.valueOf(dataRef), Date.valueOf(LocalDate.of(2024, 1, 1)), loteId);

        // Incompleto (só taxa)
        sa.update("""
                INSERT INTO ponto_dado_mercado (fonte, conjunto_dados, data_referencia, chave_instrumento, valor, tipo_cotacao, lote_ingestao_id)
                VALUES ('B3', 'BVBG.086', ?, 'DI1F25', 11.0, 'TAXA_AJUSTE', ?)
                """, Date.valueOf(dataRef), loteId);

        List<InsumoDI1> insumos = repository.buscarInsumosDI1(List.of("BVBG.086", "BVBG.028"), dataRef);

        assertThat(insumos).hasSize(2);
        
        InsumoDI1 f24 = insumos.stream().filter(i -> i.ticker().equals("DI1F24")).findFirst().get();
        assertThat(f24.taxaAjuste()).isEqualByComparingTo(BigDecimal.valueOf(12.5));
        assertThat(f24.diasUteisVencimento()).isEqualTo(252);
        assertThat(f24.dataVencimento()).isEqualTo(LocalDate.of(2024, 1, 1));
        
        InsumoDI1 f25 = insumos.stream().filter(i -> i.ticker().equals("DI1F25")).findFirst().get();
        assertThat(f25.taxaAjuste()).isEqualByComparingTo(BigDecimal.valueOf(11.0));
        assertThat(f25.diasUteisVencimento()).isNull();
        assertThat(f25.dataVencimento()).isNull();
    }

    @Test
    void ignoraInstrumentoNaoDi1MesmoPresenteNosMesmosConjuntosDeDados() {
        // Achado real (tarefa 12.1): BVBG.086/BVBG.028 trazem TODOS os derivativos da B3, não só
        // DI1 -- câmbio (DOLU26), índices de país (ARBU26) etc. também aparecem nesses arquivos.
        JdbcTemplate sa = jdbcTemplateSa();
        LocalDate dataRef = LocalDate.of(2023, 1, 1);

        sa.update("""
                INSERT INTO lote_ingestao (fonte, conjunto_dados, tipo_payload, data_referencia, lote_externo_id, id_evento, hash_payload, total_blocos, estado)
                VALUES ('B3', 'BVBG.086', 'INDIVIDUAL_QUOTES', ?, 'lote-456', 'evt-456', 'hash', 1, 'COMPLETO')
                """, Date.valueOf(dataRef));

        loteId = sa.queryForObject("SELECT MAX(id) FROM lote_ingestao", Long.class);

        sa.update("""
                INSERT INTO ponto_dado_mercado (fonte, conjunto_dados, data_referencia, chave_instrumento, valor, tipo_cotacao, lote_ingestao_id)
                VALUES ('B3', 'BVBG.086', ?, 'DI1F24', 12.5, 'TAXA_AJUSTE', ?)
                """, Date.valueOf(dataRef), loteId);
        sa.update("""
                INSERT INTO ponto_dado_mercado (fonte, conjunto_dados, data_referencia, chave_instrumento, valor, tipo_cotacao, data_vencimento, lote_ingestao_id)
                VALUES ('B3', 'BVBG.028', ?, 'DI1F24', 252, 'DIAS_UTEIS_VENCIMENTO', ?, ?)
                """, Date.valueOf(dataRef), Date.valueOf(LocalDate.of(2024, 1, 1)), loteId);

        // Instrumento real de câmbio, mesmo conjunto de dados, mesmo prazo do DI1F24 acima --
        // se não fosse filtrado, colidiria com o prazo do DI1F24 em CurvaJuros.de.
        sa.update("""
                INSERT INTO ponto_dado_mercado (fonte, conjunto_dados, data_referencia, chave_instrumento, valor, tipo_cotacao, lote_ingestao_id)
                VALUES ('B3', 'BVBG.086', ?, 'DOLU26', 5.20, 'TAXA_AJUSTE', ?)
                """, Date.valueOf(dataRef), loteId);
        sa.update("""
                INSERT INTO ponto_dado_mercado (fonte, conjunto_dados, data_referencia, chave_instrumento, valor, tipo_cotacao, data_vencimento, lote_ingestao_id)
                VALUES ('B3', 'BVBG.028', ?, 'DOLU26', 252, 'DIAS_UTEIS_VENCIMENTO', ?, ?)
                """, Date.valueOf(dataRef), Date.valueOf(LocalDate.of(2024, 1, 1)), loteId);

        List<InsumoDI1> insumos = repository.buscarInsumosDI1(List.of("BVBG.086", "BVBG.028"), dataRef);

        assertThat(insumos).hasSize(1);
        assertThat(insumos.get(0).ticker()).isEqualTo("DI1F24");
    }
}
