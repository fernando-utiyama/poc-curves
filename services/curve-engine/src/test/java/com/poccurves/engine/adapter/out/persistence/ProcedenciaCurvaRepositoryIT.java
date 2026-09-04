package com.poccurves.engine.adapter.out.persistence;
import com.poccurves.engine.domain.versao.ProcedenciaCurva;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Teste de integração real: exige o ambiente local Podman de pé (deploy/podman/up.sh --lite),
 * com o SQL Server já migrado até V18.
 */
class ProcedenciaCurvaRepositoryIT {

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

    private final ProcedenciaCurvaRepository repository = new ProcedenciaCurvaRepository(jdbcTemplateApp());

    private UUID definicaoCurvaId;
    private UUID versaoDefinicaoCurvaId;
    private UUID execucaoCurvaId;
    private UUID versaoCurvaId;
    private UUID modeloCurvaId;
    private String codigoModelo;

    @BeforeEach
    void seedFks() {
        JdbcTemplate sa = jdbcTemplateSa();
        String codigoCurva = "IT_PROCED_" + UUID.randomUUID().toString().substring(0, 8);
        codigoModelo = "IT_MODELO_" + UUID.randomUUID().toString().substring(0, 8);
        definicaoCurvaId = UUID.randomUUID();
        versaoDefinicaoCurvaId = UUID.randomUUID();
        execucaoCurvaId = UUID.randomUUID();
        versaoCurvaId = UUID.randomUUID();
        modeloCurvaId = UUID.randomUUID();

        sa.update("""
                INSERT INTO definicao_curva (id, codigo, nome, moeda, modo_origem, horario_limite_publicacao, estado, criado_por)
                VALUES (?, ?, 'teste IT', 'BRL', 'BOOTSTRAPPED', '18:00', 'ATIVA', 'teste')
                """, definicaoCurvaId, codigoCurva);
        sa.update("""
                INSERT INTO versao_definicao_curva (
                    id, definicao_curva_id, numero_versao, contagem_dias, calendario, interpolador,
                    politica_extrapolacao, politica_arredondamento, vigencia_inicio
                ) VALUES (?, ?, 1, 'ATUAL_252', 'B3', 'LINEAR', 'TAXA_CONSTANTE', 'PADRAO', '2020-01-01')
                """, versaoDefinicaoCurvaId, definicaoCurvaId);
        sa.update("""
                INSERT INTO execucao_curva (id, correlacao_id, disparo, faixa, estado)
                VALUES (?, ?, 'MANUAL', 'PRIORITARIA', 'PENDENTE')
                """, execucaoCurvaId, UUID.randomUUID());
        sa.update("""
                INSERT INTO versao_curva (
                    id, definicao_curva_id, versao_definicao_curva_id, data_referencia,
                    momento_curva, numero_versao, origem_versao, estado, execucao_curva_id
                ) VALUES (?, ?, ?, '2026-03-10', 'FECHAMENTO', 1, 'CALCULADA', 'EM_VALIDACAO', ?)
                """, versaoCurvaId, definicaoCurvaId, versaoDefinicaoCurvaId, execucaoCurvaId);
        sa.update("""
                INSERT INTO modelo_curva (id, codigo, nome, tipo, estado)
                VALUES (?, ?, 'modelo teste', 'BUILTIN', 'ATIVO')
                """, modeloCurvaId, codigoModelo);
    }

    @AfterEach
    void limpar() {
        JdbcTemplate sa = jdbcTemplateSa();
        sa.update("DELETE FROM procedencia_curva WHERE versao_curva_id = ?", versaoCurvaId);
        sa.update("DELETE FROM versao_curva WHERE id = ?", versaoCurvaId);
        sa.update("DELETE FROM execucao_curva WHERE id = ?", execucaoCurvaId);
        sa.update("DELETE FROM versao_definicao_curva WHERE id = ?", versaoDefinicaoCurvaId);
        sa.update("DELETE FROM definicao_curva WHERE id = ?", definicaoCurvaId);
        sa.update("DELETE FROM modelo_curva WHERE id = ?", modeloCurvaId);
    }

    @Test
    void inserirEBuscarPorVersaoCurvaDevolveTodosOsCampos() {
        ProcedenciaCurva procedencia = new ProcedenciaCurva(
                versaoCurvaId, execucaoCurvaId, 1,
                "checksumABC", "{\"insumos\":[\"DI1F26\"]}", "hashConjunto123",
                null, null, null, null, null,
                "1.0.0-teste", modeloCurvaId
        );

        repository.inserir(procedencia);

        Optional<ProcedenciaCurva> lida = repository.buscarPorVersaoCurva(versaoCurvaId);

        assertThat(lida).isPresent();
        assertThat(lida.get().versaoCurvaId()).isEqualTo(versaoCurvaId);
        assertThat(lida.get().execucaoCurvaId()).isEqualTo(execucaoCurvaId);
        assertThat(lida.get().numeroVersaoDefinicao()).isEqualTo(1);
        assertThat(lida.get().checksumModelo()).isEqualTo("checksumABC");
        assertThat(lida.get().referenciasInsumo()).isEqualTo("{\"insumos\":[\"DI1F26\"]}");
        assertThat(lida.get().hashConjuntoInsumos()).isEqualTo("hashConjunto123");
        assertThat(lida.get().loteIngestaoId()).isNull();
        assertThat(lida.get().versaoMotor()).isEqualTo("1.0.0-teste");
        assertThat(lida.get().modeloCurvaId()).isEqualTo(modeloCurvaId);
    }

    @Test
    void buscarPorVersaoCurvaSemProcedenciaDevolveVazio() {
        assertThat(repository.buscarPorVersaoCurva(UUID.randomUUID())).isEmpty();
    }
}
