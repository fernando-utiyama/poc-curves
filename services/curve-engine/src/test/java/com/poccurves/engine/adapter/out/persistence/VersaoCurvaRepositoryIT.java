package com.poccurves.engine.adapter.out.persistence;
import com.poccurves.engine.application.model.MomentoCurva;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Teste de integração real: exige o ambiente local Podman de pé (deploy/podman/up.sh --lite),
 * com o SQL Server já migrado até V18. {@code inserir}/{@code atualizar}/{@code
 * proximoNumeroVersao}/{@code buscarPorId}/{@code buscarPorExecucaoCurvaId}/{@code
 * buscarUltimaVersaoPublicadaAnterior} foram removidos junto com a limpeza da curva DI1/
 * BOOTSTRAPPED (BVBG.086/BVBG.028) — só a leitura usada por {@code InterpolacaoService}
 * (curvas IMPORTED, ex. B3_CURVA_PRE) sobrevive. Semeia a versão diretamente via {@code sa},
 * já que o repositório não escreve mais.
 */
class VersaoCurvaRepositoryIT {

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

    private final VersaoCurvaRepository repository = new VersaoCurvaRepository(jdbcTemplateApp());

    private String codigoCurva;
    private UUID definicaoCurvaId;
    private UUID versaoDefinicaoCurvaId;
    private UUID execucaoCurvaId;

    @BeforeEach
    void seedFks() {
        JdbcTemplate sa = jdbcTemplateSa();
        codigoCurva = "IT_VERSAO_" + UUID.randomUUID().toString().substring(0, 8);
        definicaoCurvaId = UUID.randomUUID();
        versaoDefinicaoCurvaId = UUID.randomUUID();
        execucaoCurvaId = UUID.randomUUID();

        sa.update("""
                INSERT INTO definicao_curva (id, codigo, nome, moeda, modo_origem, horario_limite_publicacao, estado, criado_por)
                VALUES (?, ?, 'teste IT', 'BRL', 'IMPORTED', '18:00', 'ATIVA', 'teste')
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
    }

    @AfterEach
    void limpar() {
        JdbcTemplate sa = jdbcTemplateSa();
        sa.update("DELETE FROM versao_curva WHERE definicao_curva_id = ?", definicaoCurvaId);
        sa.update("DELETE FROM execucao_curva WHERE id = ?", execucaoCurvaId);
        sa.update("DELETE FROM versao_definicao_curva WHERE id = ?", versaoDefinicaoCurvaId);
        sa.update("DELETE FROM definicao_curva WHERE id = ?", definicaoCurvaId);
    }

    private void seedVersaoCurva(UUID id, LocalDate dataReferencia, int numeroVersao, String estado, Instant publicadoEm) {
        jdbcTemplateSa().update("""
                INSERT INTO versao_curva (
                    id, definicao_curva_id, versao_definicao_curva_id, data_referencia,
                    momento_curva, numero_versao, origem_versao, estado, execucao_curva_id, publicado_em
                ) VALUES (?, ?, ?, ?, 'FECHAMENTO', ?, 'IMPORTADA', ?, ?, ?)
                """, id, definicaoCurvaId, versaoDefinicaoCurvaId, dataReferencia, numeroVersao, estado,
                execucaoCurvaId, publicadoEm != null ? Timestamp.from(publicadoEm) : null);
    }

    @Test
    void buscarVersaoVigentePublicadaEBuscarPorNumeroVersaoLeemOQueFoiSemeado() {
        LocalDate dataReferencia = LocalDate.of(2026, 3, 10);
        UUID versaoId = UUID.randomUUID();
        seedVersaoCurva(versaoId, dataReferencia, 1, "PUBLICADA", Instant.now());

        Optional<com.poccurves.engine.application.model.VersaoCurva> publicada =
                repository.buscarVersaoVigentePublicada(definicaoCurvaId, dataReferencia, MomentoCurva.FECHAMENTO);
        assertThat(publicada).isPresent();
        assertThat(publicada.get().id()).isEqualTo(versaoId);
        assertThat(publicada.get().estado().name()).isEqualTo("PUBLICADA");
        assertThat(publicada.get().publicadoEm()).isNotNull();

        Optional<com.poccurves.engine.application.model.VersaoCurva> porNumero =
                repository.buscarPorNumeroVersao(definicaoCurvaId, dataReferencia, MomentoCurva.FECHAMENTO, 1);
        assertThat(porNumero).isPresent();
        assertThat(porNumero.get().id()).isEqualTo(versaoId);
    }

    @Test
    void buscarVersaoVigentePublicadaNaoEncontraVersaoNaoPublicada() {
        LocalDate dataReferencia = LocalDate.of(2026, 3, 11);
        seedVersaoCurva(UUID.randomUUID(), dataReferencia, 1, "EM_VALIDACAO", null);

        assertThat(repository.buscarVersaoVigentePublicada(definicaoCurvaId, dataReferencia, MomentoCurva.FECHAMENTO)).isEmpty();
    }
}
