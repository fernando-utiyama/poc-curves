package com.poccurves.engine.adapter.out.persistence;
import com.poccurves.engine.application.model.VerticeBtrs;
import com.poccurves.engine.application.model.VerticeConstruido;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Teste de integração real: exige o ambiente local Podman de pé (deploy/podman/up.sh --lite),
 * com o SQL Server já migrado até V26. Cobre as 4 leituras/escritas novas da construção das
 * curvas TS B3 (openspec/changes/b3-additional-curves): tBtrsCurvaPrimr (leitura), tConfgCurva
 * (leitura), tDadoCurva e tCurvaData (escrita).
 */
class ConstrucaoCurvaB3RepositoriesIT {

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

    private final BtrsCurvaPrimrConsultaRepository btrsRepository = new BtrsCurvaPrimrConsultaRepository(jdbcTemplateApp());
    private final ConfgCurvaRepository confgCurvaRepository = new ConfgCurvaRepository(jdbcTemplateApp());
    private final DadoCurvaRepository dadoCurvaRepository = new DadoCurvaRepository(jdbcTemplateApp());
    private final CurvaDataRepository curvaDataRepository = new CurvaDataRepository(jdbcTemplateApp());

    private static final String TICKER = "IT_B3_REPO_TESTE";
    private static final LocalDate DATA_REF = LocalDate.of(2026, 9, 14);

    @AfterEach
    void limpar() {
        JdbcTemplate sa = jdbcTemplateSa();
        sa.update("DELETE FROM tCurvaData WHERE cTickerIndcd = ?", TICKER);
        sa.update("DELETE FROM tDadoCurva WHERE cTickerIndcd = ?", TICKER);
        sa.update("DELETE FROM tConfgCurva WHERE cTickerIndcd = ?", TICKER);
        sa.update("DELETE FROM tBtrsCurvaPrimr WHERE cTickerIndcd = ?", TICKER);
        sa.update("DELETE FROM tCurvaMercd WHERE cTickerIndcd = ?", TICKER);
    }

    @Test
    void leGravaTBtrsCurvaPrimrTConfgCurvaTDadoCurvaTCurvaDataDePontaAPonta() {
        JdbcTemplate sa = jdbcTemplateSa();
        sa.update("""
                INSERT INTO tCurvaMercd (cTickerIndcd, cClasfInstt, cClassAtivo, cMoedaNegoc, dInicVgcia, cUsuarCalc)
                VALUES (?, 'CURVA_SWAP', 'TAXA_JUROS', 'BRL', '2020-01-01', 'teste IT')
                """, TICKER);
        sa.update("""
                INSERT INTO tConfgCurva (cTickerIndcd, cAtivoFincr, cMotorCalc, cTpoInstt, cVrsaoReg, dInicVgcia)
                VALUES (?, 1, 'LINEAR', 'CURVA_SWAP', 1, '2020-01-01')
                """, TICKER);
        sa.update(
                "INSERT INTO tBtrsCurvaPrimr (cldtfdUnic, cTickerIndcd, cDiaCorri, cDiaUtil, dBaseReft, vPrecoTx) VALUES (NEXT VALUE FOR seq_tbtrscurvaprimr_cidtfdunic, ?, ?, ?, ?, ?)",
                TICKER, 1, 1, java.sql.Date.valueOf(DATA_REF), new BigDecimal("13.90"));
        sa.update(
                "INSERT INTO tBtrsCurvaPrimr (cldtfdUnic, cTickerIndcd, cDiaCorri, cDiaUtil, dBaseReft, vPrecoTx) VALUES (NEXT VALUE FOR seq_tbtrscurvaprimr_cidtfdunic, ?, ?, ?, ?, ?)",
                TICKER, 30, 21, java.sql.Date.valueOf(DATA_REF), new BigDecimal("13.85"));

        List<VerticeBtrs> lidos = btrsRepository.buscarVertices(TICKER, DATA_REF);
        assertThat(lidos).hasSize(2);
        assertThat(lidos.get(0).diasUteis()).isEqualTo(1);
        assertThat(lidos.get(0).diasCorridos()).isEqualTo(1);
        assertThat(lidos.get(0).taxa()).isEqualByComparingTo("13.90");
        assertThat(lidos.get(1).diasUteis()).isEqualTo(21);

        assertThat(confgCurvaRepository.buscarMotorCalcVigente(TICKER, DATA_REF)).contains("LINEAR");
        assertThat(confgCurvaRepository.buscarMotorCalcVigente("TICKER_INEXISTENTE", DATA_REF)).isEmpty();

        List<VerticeConstruido> construidos = List.of(
                new VerticeConstruido(DATA_REF.plusDays(1), new BigDecimal("13.90")),
                new VerticeConstruido(DATA_REF.plusDays(30), new BigDecimal("13.85")));

        curvaDataRepository.excluirPontos(TICKER, DATA_REF);
        dadoCurvaRepository.substituirVertices(TICKER, DATA_REF, construidos);
        curvaDataRepository.inserirPontos(TICKER, DATA_REF, construidos);

        List<java.util.Map<String, Object>> dados = sa.queryForList(
                "SELECT dVertcReft, vPrecoTx FROM tDadoCurva WHERE cTickerIndcd = ? ORDER BY dVertcReft", TICKER);
        assertThat(dados).hasSize(2);
        assertThat(((java.sql.Date) dados.get(0).get("dVertcReft")).toLocalDate()).isEqualTo(DATA_REF.plusDays(1));

        List<java.util.Map<String, Object>> curva = sa.queryForList(
                "SELECT dVertcReft, vPrecoTx FROM tCurvaData WHERE cTickerIndcd = ? ORDER BY dVertcReft", TICKER);
        assertThat(curva).hasSize(2);

        // Reprocessamento idempotente: substitui, não duplica, e não viola FK_tDadoCurva_tCurvaData
        // (tCurvaData tem que ser esvaziado antes de tDadoCurva ser apagado/reinserido).
        curvaDataRepository.excluirPontos(TICKER, DATA_REF);
        dadoCurvaRepository.substituirVertices(TICKER, DATA_REF, construidos);
        curvaDataRepository.inserirPontos(TICKER, DATA_REF, construidos);
        assertThat(sa.queryForObject("SELECT COUNT(*) FROM tDadoCurva WHERE cTickerIndcd = ?", Integer.class, TICKER)).isEqualTo(2);
        assertThat(sa.queryForObject("SELECT COUNT(*) FROM tCurvaData WHERE cTickerIndcd = ?", Integer.class, TICKER)).isEqualTo(2);
    }
}
