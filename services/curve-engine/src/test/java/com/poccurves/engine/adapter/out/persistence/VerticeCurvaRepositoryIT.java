package com.poccurves.engine.adapter.out.persistence;
import com.poccurves.engine.application.model.Vertice;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Teste de integração real: exige o ambiente local Podman de pé (deploy/podman/up.sh --lite),
 * com o SQL Server já migrado até V18.
 */
class VerticeCurvaRepositoryIT {

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

    private final VerticeCurvaRepository repository = new VerticeCurvaRepository(jdbcTemplateApp());

    private UUID definicaoCurvaId;
    private UUID versaoDefinicaoCurvaId;
    private UUID execucaoCurvaId;
    private UUID versaoCurvaId;

    @BeforeEach
    void seedFks() {
        JdbcTemplate sa = jdbcTemplateSa();
        String codigoCurva = "IT_VERTICE_" + UUID.randomUUID().toString().substring(0, 8);
        definicaoCurvaId = UUID.randomUUID();
        versaoDefinicaoCurvaId = UUID.randomUUID();
        execucaoCurvaId = UUID.randomUUID();
        versaoCurvaId = UUID.randomUUID();

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
    }

    @AfterEach
    void limpar() {
        JdbcTemplate sa = jdbcTemplateSa();
        sa.update("DELETE FROM vertice_curva WHERE versao_curva_id = ?", versaoCurvaId);
        sa.update("DELETE FROM versao_curva WHERE id = ?", versaoCurvaId);
        sa.update("DELETE FROM execucao_curva WHERE id = ?", execucaoCurvaId);
        sa.update("DELETE FROM versao_definicao_curva WHERE id = ?", versaoDefinicaoCurvaId);
        sa.update("DELETE FROM definicao_curva WHERE id = ?", definicaoCurvaId);
    }

    @Test
    void inserirTodosEBuscarPorVersaoCurvaDevolveOrdenadoPorPrazo() {
        // Ordem de entrada de propósito invertida (252 antes de 21) -- a query tem que ordenar, não a lista.
        List<Vertice> desordenados = List.of(
                new Vertice(252, null, LocalDate.of(2027, 1, 4), new BigDecimal("0.1200"), new BigDecimal("0.892857")),
                new Vertice(21, null, LocalDate.of(2026, 4, 10), new BigDecimal("0.1000"), null)
        );

        repository.inserirTodos(versaoCurvaId, desordenados);

        List<Vertice> lidos = repository.buscarPorVersaoCurva(versaoCurvaId);

        assertThat(lidos).hasSize(2);
        assertThat(lidos.get(0).prazoDiasUteis()).isEqualTo(21);
        assertThat(lidos.get(0).taxa()).isEqualByComparingTo("0.1000");
        assertThat(lidos.get(0).fatorDesconto()).isNull();
        assertThat(lidos.get(0).prazoDiasCorridos()).isNull();

        assertThat(lidos.get(1).prazoDiasUteis()).isEqualTo(252);
        assertThat(lidos.get(1).taxa()).isEqualByComparingTo("0.1200");
        assertThat(lidos.get(1).fatorDesconto()).isEqualByComparingTo("0.892857");
        assertThat(lidos.get(1).dataVencimento()).isEqualTo(LocalDate.of(2027, 1, 4));
    }
}
