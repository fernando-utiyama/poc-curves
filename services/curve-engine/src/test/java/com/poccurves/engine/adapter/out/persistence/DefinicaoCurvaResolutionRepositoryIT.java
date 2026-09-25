package com.poccurves.engine.adapter.out.persistence;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.sql.Date;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Teste de integração real: exige o ambiente local Podman de pé (deploy/podman/up.sh --lite).
 * {@code resolverVigente} (curva DI1/BOOTSTRAPPED) foi removido junto com a limpeza de
 * BVBG.086/BVBG.028 — só {@link DefinicaoCurvaResolutionRepository#resolverIdPorCodigo} e
 * {@link DefinicaoCurvaResolutionRepository#resolverConfiguracaoInterpolacao} sobrevivem, usados
 * hoje pela API de interpolação para as curvas IMPORTED (ex. B3_CURVA_PRE).
 */
class DefinicaoCurvaResolutionRepositoryIT {

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

    private final DefinicaoCurvaResolutionRepository repository = new DefinicaoCurvaResolutionRepository(jdbcTemplateApp());

    private UUID definicaoId;
    private UUID versaoVigenteId;

    @AfterEach
    void limpar() {
        if (definicaoId != null) {
            JdbcTemplate sa = jdbcTemplateSa();
            sa.update("DELETE FROM versao_definicao_curva WHERE definicao_curva_id = ?", definicaoId);
            sa.update("DELETE FROM definicao_curva WHERE id = ?", definicaoId);
        }
    }

    @Test
    void resolverIdPorCodigoEncontraDefinicaoExistenteEDevolveVazioParaCodigoInexistente() {
        String codigo = "IT_DEF_" + UUID.randomUUID().toString().substring(0, 8);
        definicaoId = UUID.randomUUID();

        JdbcTemplate sa = jdbcTemplateSa();
        sa.update("""
                INSERT INTO definicao_curva (id, codigo, nome, moeda, modo_origem, horario_limite_publicacao, estado, criado_por)
                VALUES (?, ?, 'Def Teste', 'BRL', 'IMPORTED', '18:00:00', 'ATIVA', 'teste')
                """, definicaoId, codigo);

        assertThat(repository.resolverIdPorCodigo(codigo)).contains(definicaoId);
        assertThat(repository.resolverIdPorCodigo("CODIGO_QUE_NAO_EXISTE_" + UUID.randomUUID())).isEmpty();
    }

    @Test
    void resolverConfiguracaoInterpolacaoAchaVersaoVigenteNaData() {
        String codigo = "IT_DEF_CFG_" + UUID.randomUUID().toString().substring(0, 8);
        definicaoId = UUID.randomUUID();
        versaoVigenteId = UUID.randomUUID();

        JdbcTemplate sa = jdbcTemplateSa();
        sa.update("""
                INSERT INTO definicao_curva (id, codigo, nome, moeda, modo_origem, horario_limite_publicacao, estado, criado_por)
                VALUES (?, ?, 'Def Teste', 'BRL', 'IMPORTED', '18:00:00', 'ATIVA', 'teste')
                """, definicaoId, codigo);
        sa.update("""
                INSERT INTO versao_definicao_curva (id, definicao_curva_id, numero_versao, contagem_dias, calendario, interpolador, politica_extrapolacao, politica_arredondamento, vigencia_inicio, vigencia_fim)
                VALUES (?, ?, 1, 'UTEIS', 'BR_B3', 'LOG_LINEAR', 'FLAT', 'HALF_UP', ?, NULL)
                """, versaoVigenteId, definicaoId, Date.valueOf(LocalDate.now()));

        Optional<com.poccurves.engine.application.construcao.ConfiguracaoInterpolacao> config =
                repository.resolverConfiguracaoInterpolacao(codigo, LocalDate.now());

        assertThat(config).isPresent();
        assertThat(config.get().interpolador()).isEqualTo("LOG_LINEAR");
    }
}
