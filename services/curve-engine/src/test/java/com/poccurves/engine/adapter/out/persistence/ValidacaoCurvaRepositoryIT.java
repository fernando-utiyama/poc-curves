package com.poccurves.engine.adapter.out.persistence;
import com.poccurves.engine.application.model.Classificacao;
import com.poccurves.engine.application.model.ResultadoTeste;
import com.poccurves.engine.application.model.ResultadoValidacao;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Teste de integração real: exige o ambiente local Podman de pé (deploy/podman/up.sh --lite),
 * com o SQL Server já migrado até V18.
 */
class ValidacaoCurvaRepositoryIT {

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

    private final ValidacaoCurvaRepository repository = new ValidacaoCurvaRepository(jdbcTemplateApp());

    private UUID definicaoCurvaId;
    private UUID versaoDefinicaoCurvaId;
    private UUID execucaoCurvaId;
    private UUID versaoCurvaId;

    @BeforeEach
    void seedFks() {
        JdbcTemplate sa = jdbcTemplateSa();
        String codigoCurva = "IT_VALID_" + UUID.randomUUID().toString().substring(0, 8);
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
        sa.update("DELETE FROM validacao_curva WHERE versao_curva_id = ?", versaoCurvaId);
        sa.update("DELETE FROM versao_curva WHERE id = ?", versaoCurvaId);
        sa.update("DELETE FROM execucao_curva WHERE id = ?", execucaoCurvaId);
        sa.update("DELETE FROM versao_definicao_curva WHERE id = ?", versaoDefinicaoCurvaId);
        sa.update("DELETE FROM definicao_curva WHERE id = ?", definicaoCurvaId);
    }

    @Test
    void inserirTodosEBuscarPorVersaoCurvaDevolveTodosOsResultados() {
        List<ResultadoTeste> resultados = List.of(
                new ResultadoTeste("MONOTONICIDADE_FATOR_DESCONTO", Classificacao.BLOQUEANTE, ResultadoValidacao.APROVADO,
                        new BigDecimal("0.01"), new BigDecimal("0.05"), null),
                new ResultadoTeste("VARIACAO_DIA_ANTERIOR", Classificacao.AVISO, ResultadoValidacao.NAO_APLICAVEL,
                        null, null, "sem curva anterior publicada")
        );

        repository.inserirTodos(versaoCurvaId, resultados);

        List<ResultadoTeste> lidos = repository.buscarPorVersaoCurva(versaoCurvaId);

        assertThat(lidos).hasSize(2);
        assertThat(lidos.get(0).identificador()).isEqualTo("MONOTONICIDADE_FATOR_DESCONTO");
        assertThat(lidos.get(0).classificacao()).isEqualTo(Classificacao.BLOQUEANTE);
        assertThat(lidos.get(0).resultado()).isEqualTo(ResultadoValidacao.APROVADO);
        assertThat(lidos.get(0).medidaObservada()).isEqualByComparingTo("0.01");
        assertThat(lidos.get(0).limiteAplicado()).isEqualByComparingTo("0.05");

        assertThat(lidos.get(1).identificador()).isEqualTo("VARIACAO_DIA_ANTERIOR");
        assertThat(lidos.get(1).resultado()).isEqualTo(ResultadoValidacao.NAO_APLICAVEL);
        assertThat(lidos.get(1).medidaObservada()).isNull();
        assertThat(lidos.get(1).detalhe()).isEqualTo("sem curva anterior publicada");
    }
}
