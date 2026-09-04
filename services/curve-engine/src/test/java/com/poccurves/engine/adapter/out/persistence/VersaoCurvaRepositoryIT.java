package com.poccurves.engine.adapter.out.persistence;
import com.poccurves.engine.application.model.MomentoCurva;
import com.poccurves.engine.application.model.OrigemVersao;
import com.poccurves.engine.application.model.VersaoCurva;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Teste de integração real: exige o ambiente local Podman de pé (deploy/podman/up.sh --lite),
 * com o SQL Server já migrado até V18. Nomeado com sufixo "IT" — fora do build padrão (mvn test
 * só coleta **&#47;*Test.java).
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

    private final JdbcTemplate jdbcTemplate = jdbcTemplateApp();
    private final VersaoCurvaRepository repository = new VersaoCurvaRepository(jdbcTemplate);

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
    }

    @AfterEach
    void limpar() {
        JdbcTemplate sa = jdbcTemplateSa();
        sa.update("DELETE FROM versao_curva WHERE definicao_curva_id = ?", definicaoCurvaId);
        sa.update("DELETE FROM execucao_curva WHERE id = ?", execucaoCurvaId);
        sa.update("DELETE FROM versao_definicao_curva WHERE id = ?", versaoDefinicaoCurvaId);
        sa.update("DELETE FROM definicao_curva WHERE id = ?", definicaoCurvaId);
    }

    @Test
    void fluxoCompletoInserirBuscarPublicarProximoNumero() {
        LocalDate dataReferencia = LocalDate.of(2026, 3, 10);

        assertThat(repository.proximoNumeroVersao(definicaoCurvaId, dataReferencia, MomentoCurva.FECHAMENTO)).isEqualTo(1);

        VersaoCurva versao = VersaoCurva.criar(
                definicaoCurvaId, versaoDefinicaoCurvaId, dataReferencia, MomentoCurva.FECHAMENTO,
                1, OrigemVersao.CALCULADA, execucaoCurvaId
        );
        repository.inserir(versao);

        Optional<VersaoCurva> lida = repository.buscarPorId(versao.id());
        assertThat(lida).isPresent();
        assertThat(lida.get().estado().name()).isEqualTo("EM_VALIDACAO");
        assertThat(lida.get().dataReferencia()).isEqualTo(dataReferencia);

        Optional<VersaoCurva> lidaPorExecucao = repository.buscarPorExecucaoCurvaId(execucaoCurvaId);
        assertThat(lidaPorExecucao).isPresent();
        assertThat(lidaPorExecucao.get().id()).isEqualTo(versao.id());

        assertThat(repository.buscarVersaoVigentePublicada(definicaoCurvaId, dataReferencia, MomentoCurva.FECHAMENTO)).isEmpty();

        versao.publicar();
        repository.atualizar(versao);

        Optional<VersaoCurva> publicada = repository.buscarVersaoVigentePublicada(definicaoCurvaId, dataReferencia, MomentoCurva.FECHAMENTO);
        assertThat(publicada).isPresent();
        assertThat(publicada.get().estado().name()).isEqualTo("PUBLICADA");
        assertThat(publicada.get().publicadoEm()).isNotNull();

        assertThat(repository.proximoNumeroVersao(definicaoCurvaId, dataReferencia, MomentoCurva.FECHAMENTO)).isEqualTo(2);
    }

    @Test
    void buscarUltimaVersaoPublicadaAnteriorEncontraDataMaisRecenteAntesDaInformada() {
        LocalDate dataAntiga = LocalDate.of(2026, 3, 5);
        LocalDate dataRecente = LocalDate.of(2026, 3, 9);
        LocalDate dataConsulta = LocalDate.of(2026, 3, 10);

        VersaoCurva versaoAntiga = VersaoCurva.criar(
                definicaoCurvaId, versaoDefinicaoCurvaId, dataAntiga, MomentoCurva.FECHAMENTO,
                1, OrigemVersao.CALCULADA, execucaoCurvaId
        );
        versaoAntiga.publicar();
        repository.inserir(versaoAntiga);

        VersaoCurva versaoRecente = VersaoCurva.criar(
                definicaoCurvaId, versaoDefinicaoCurvaId, dataRecente, MomentoCurva.FECHAMENTO,
                1, OrigemVersao.CALCULADA, execucaoCurvaId
        );
        versaoRecente.publicar();
        repository.inserir(versaoRecente);

        Optional<VersaoCurva> encontrada = repository.buscarUltimaVersaoPublicadaAnterior(definicaoCurvaId, dataConsulta, MomentoCurva.FECHAMENTO);

        assertThat(encontrada).isPresent();
        assertThat(encontrada.get().dataReferencia()).isEqualTo(dataRecente);
    }
}
