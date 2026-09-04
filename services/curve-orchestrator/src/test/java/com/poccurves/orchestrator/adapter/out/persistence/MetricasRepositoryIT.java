package com.poccurves.orchestrator.adapter.out.persistence;

import com.poccurves.orchestrator.domain.EstadoExecucao;
import com.poccurves.orchestrator.domain.ExecucaoCurva;
import com.poccurves.orchestrator.domain.Faixa;
import com.poccurves.orchestrator.domain.MomentoCurva;
import com.poccurves.orchestrator.domain.TipoDisparo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Teste de integração real: exige o ambiente local Podman de pé
 * (`deploy/podman/up.sh --lite`). Nomeado com sufixo "IT" — fora do build padrão.
 */
class MetricasRepositoryIT {

    private static JdbcTemplate jdbcTemplate() {
        DriverManagerDataSource ds = new DriverManagerDataSource(
                "jdbc:sqlserver://localhost:1433;databaseName=curvasdb;trustServerCertificate=true;encrypt=true",
                "curve_orchestrator_app", "CurveOrchestratorP0c!Local");
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

    private final JdbcTemplate jdbcTemplate = jdbcTemplate();
    private final ExecucaoCurvaRepository execucaoRepository = new ExecucaoCurvaRepository(jdbcTemplate);
    private final MetricasRepository metricasRepository = new MetricasRepository(jdbcTemplate);

    @AfterEach
    void limpar() {
        jdbcTemplateSa().update("DELETE FROM execucao_curva WHERE conjunto_dados LIKE 'IT_METRICAS_%'");
    }

    private ExecucaoCurva criar(String conjunto) {
        ExecucaoCurva execucao = ExecucaoCurva.iniciar(
                UUID.randomUUID(), null, null, conjunto, LocalDate.now(),
                MomentoCurva.INTRADIA, TipoDisparo.MANUAL, "teste", Faixa.ROTINA, null, null
        );
        execucaoRepository.inserir(execucao);
        return execucao;
    }

    @Test
    void contarPorEstadoTrazTodosOsOitoEstadosMesmoZerados() {
        Map<String, Integer> contagem = metricasRepository.contarPorEstado();

        assertThat(contagem).hasSize(EstadoExecucao.values().length);
        for (EstadoExecucao estado : EstadoExecucao.values()) {
            assertThat(contagem).containsKey(estado.name());
        }
    }

    @Test
    void mediaTentativasEDuracaoMediaRefletemExecucoesReaisConcluidas() {
        String conjunto = "IT_METRICAS_" + UUID.randomUUID().toString().substring(0, 8);

        ExecucaoCurva concluida = criar(conjunto);
        concluida.iniciarExecucao();
        concluida.incrementarTentativa();
        concluida.incrementarTentativa();
        concluida.iniciarConstrucao();
        concluida.concluir();
        execucaoRepository.atualizar(concluida);

        Map<String, Integer> contagem = metricasRepository.contarPorEstado();
        // Agregado global (não filtrado por conjunto) — só provamos que a execução real recém-concluída
        // está contada em algum lugar, não um baseline exato (outros IT do módulo também escrevem na tabela).
        assertThat(contagem.get("CONCLUIDA")).isGreaterThanOrEqualTo(1);

        Double mediaTentativas = metricasRepository.mediaTentativas();
        assertThat(mediaTentativas).isNotNull();
        assertThat(mediaTentativas).isGreaterThanOrEqualTo(0.0);

        Double duracaoMedia = metricasRepository.duracaoMediaSegundos();
        assertThat(duracaoMedia).isNotNull();
        assertThat(duracaoMedia).isGreaterThanOrEqualTo(0.0);
    }
}
