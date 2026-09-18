package com.poccurves.api.adapter.out.persistence;

import com.poccurves.api.domain.CurvaMercado;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Teste de integração real: exige o ambiente local Podman de pé (deploy/podman/up.sh --lite),
 * com o SQL Server já migrado até V28. Prova a leitura de {@code tCurvaMercd} com a credencial
 * real e restrita `curve_api_app` (só SELECT) — mesmo padrão de
 * {@code ConstrucaoCurvaB3RepositoriesIT} (curve-engine).
 */
class CurvaMercadoRepositoryIT {

    private static JdbcTemplate jdbcTemplateApp() {
        DriverManagerDataSource ds = new DriverManagerDataSource(
                "jdbc:sqlserver://localhost:1433;databaseName=curvasdb;trustServerCertificate=true;encrypt=true",
                "curve_api_app", "CurveApiP0c!Local");
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

    private final CurvaMercadoRepository repository = new CurvaMercadoRepository(jdbcTemplateApp());

    private static final String TICKER = "IT_API_CURVA_MERCD_TESTE";

    @AfterEach
    void limpar() {
        jdbcTemplateSa().update("DELETE FROM tCurvaMercd WHERE cTickerIndcd = ?", TICKER);
    }

    @Test
    void buscarPorTickerRetornaACurvaSemeadaComACredencialRestrita() {
        jdbcTemplateSa().update("""
                INSERT INTO tCurvaMercd (cTickerIndcd, cClasfInstt, cClassAtivo, cMoedaNegoc, dInicVgcia, cUsuarCalc)
                VALUES (?, 'CURVA_SWAP', 'TAXA_JUROS', 'BRL', '2020-01-01', 'teste IT')
                """, TICKER);

        Optional<CurvaMercado> encontrada = repository.buscarPorTicker(TICKER);

        assertThat(encontrada).isPresent();
        assertThat(encontrada.get().tickerIndcd()).isEqualTo(TICKER);
        assertThat(encontrada.get().classfInstt()).isEqualTo("CURVA_SWAP");
        assertThat(encontrada.get().classAtivo()).isEqualTo("TAXA_JUROS");
        assertThat(encontrada.get().moedaNegoc()).isEqualTo("BRL");
        assertThat(encontrada.get().inicVigencia()).isEqualTo(LocalDate.of(2020, 1, 1));
        assertThat(encontrada.get().usuarCalc()).isEqualTo("teste IT");
    }

    @Test
    void buscarPorTickerRetornaVazioQuandoNaoExiste() {
        assertThat(repository.buscarPorTicker("TICKER_INEXISTENTE_IT")).isEmpty();
    }

    @Test
    void listarTodasIncluiAsCincoCurvasTsB3RealmenteSeedadas() {
        List<CurvaMercado> todas = repository.listarTodas();

        assertThat(todas).extracting(CurvaMercado::tickerIndcd).contains(
                "B3_TAXA_SWAP_PRE", "B3_TAXA_SWAP_DCL", "B3_TAXA_SWAP_PTX",
                "B3_TAXA_SWAP_INP", "B3_TAXA_SWAP_DPL");
        // ORDER BY cTickerIndcd na query — confere que a leitura já vem ordenada.
        List<String> tickers = todas.stream().map(CurvaMercado::tickerIndcd).toList();
        assertThat(tickers).isSorted();
    }
}
