package com.poccurves.api.adapter.out.persistence;

import com.poccurves.api.domain.PontoCurva;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Teste de integração real: exige o ambiente local Podman de pé (deploy/podman/up.sh --lite),
 * com o SQL Server já migrado até V28. Smoke test só-leitura sobre a curva real
 * B3_TAXA_SWAP_DCL em dBaseReft = 2026-09-14 (278 vértices já gravados pelo curve-engine em
 * tDadoCurva/tCurvaData) — não semeia nem apaga nada, então não precisa de @AfterEach.
 */
class CurvaDadosRepositoryIT {

    private static JdbcTemplate jdbcTemplateApp() {
        DriverManagerDataSource ds = new DriverManagerDataSource(
                "jdbc:sqlserver://localhost:1433;databaseName=curvasdb;trustServerCertificate=true;encrypt=true",
                "curve_api_app", "CurveApiP0c!Local");
        ds.setDriverClassName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
        return new JdbcTemplate(ds);
    }

    private final CurvaDadosRepository repository = new CurvaDadosRepository(jdbcTemplateApp());

    private static final String TICKER = "B3_TAXA_SWAP_DCL";
    private static final LocalDate DATA_REF = LocalDate.of(2026, 9, 14);

    @Test
    void buscarVerticesLeTDadoCurvaComACredencialRestrita() {
        List<PontoCurva> vertices = repository.buscarVertices(TICKER, DATA_REF);

        assertThat(vertices).hasSize(278);
        assertThat(vertices).isSortedAccordingTo(java.util.Comparator.comparing(PontoCurva::dataVertice));
        assertThat(vertices.get(0).valor()).isNotNull();
    }

    @Test
    void buscarCurvaConstruidaLeTCurvaDataComACredencialRestrita() {
        List<PontoCurva> curva = repository.buscarCurvaConstruida(TICKER, DATA_REF);

        assertThat(curva).hasSize(278);
        assertThat(curva).isSortedAccordingTo(java.util.Comparator.comparing(PontoCurva::dataVertice));
    }

    @Test
    void buscarVerticesRetornaListaVaziaQuandoNaoHaDadosParaOTickerOuData() {
        assertThat(repository.buscarVertices("TICKER_INEXISTENTE_IT", DATA_REF)).isEmpty();
        assertThat(repository.buscarCurvaConstruida(TICKER, LocalDate.of(1999, 1, 1))).isEmpty();
    }
}
