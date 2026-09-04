package com.poccurves.orchestrator.adapter.out.persistence;

import com.poccurves.orchestrator.domain.ExecucaoCurva;
import com.poccurves.orchestrator.domain.Faixa;
import com.poccurves.orchestrator.domain.MomentoCurva;
import com.poccurves.orchestrator.domain.TipoDisparo;
import com.poccurves.orchestrator.domain.ProgressoBackfill;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class BackfillRepositoryIT {

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
    private final BackfillRepository backfillRepository = new BackfillRepository(jdbcTemplate);

    @AfterEach
    void limpar() {
        JdbcTemplate sa = jdbcTemplateSa();
        sa.update("DELETE FROM backfill_execucao WHERE execucao_curva_id IN (SELECT id FROM execucao_curva WHERE conjunto_dados LIKE 'IT_%')");
        sa.update("DELETE FROM execucao_curva WHERE conjunto_dados LIKE 'IT_%'");
    }

    @Test
    void fluxoCompletoDeBackfillPersistenciaEProgresso() {
        String conjunto = "IT_BACKFILL_" + UUID.randomUUID().toString().substring(0, 8);
        LocalDate start = LocalDate.of(2023, 10, 1);
        LocalDate end = LocalDate.of(2023, 10, 5);

        // 1. Inserir Mãe
        ExecucaoCurva mae = ExecucaoCurva.iniciar(
                UUID.randomUUID(), null, null, conjunto, start,
                MomentoCurva.INTRADIA, TipoDisparo.BACKFILL, "teste", Faixa.MASSA, null, null
        );
        execucaoRepository.inserir(mae);

        // 2. Inserir extensão Backfill
        backfillRepository.inserirBackfillExecucao(mae.id(), end, 5);

        Optional<LocalDate> dataFim = backfillRepository.buscarDataReferenciaFinal(mae.id());
        assertThat(dataFim).isPresent().contains(end);

        // 3. Solicitar e verificar interrupção
        assertThat(backfillRepository.interrupcaoSolicitada(mae.id())).isFalse();
        backfillRepository.solicitarInterrupcao(mae.id());
        assertThat(backfillRepository.interrupcaoSolicitada(mae.id())).isTrue();

        // 4. Inserir filhas com diferentes estados
        // Filha 1 -> CONCLUIDA
        ExecucaoCurva f1 = criarFilha(mae.id(), conjunto, LocalDate.of(2023, 10, 2));
        f1.iniciarExecucao();
        f1.iniciarConstrucao();
        f1.concluir();
        execucaoRepository.atualizar(f1);

        // Filha 2 -> SEM_DADO
        ExecucaoCurva f2 = criarFilha(mae.id(), conjunto, LocalDate.of(2023, 10, 3));
        f2.iniciarExecucao();
        f2.marcarSemDado("NO_DATA_IT");
        execucaoRepository.atualizar(f2);

        // Filha 3 -> FALHOU
        ExecucaoCurva f3 = criarFilha(mae.id(), conjunto, LocalDate.of(2023, 10, 4));
        f3.iniciarExecucao();
        f3.falhar("ERR", "msg");
        execucaoRepository.atualizar(f3);

        // Filha 4 -> PENDENTE / EXECUTANDO (vamos deixar EXECUTANDO para testar pendente no agrupamento)
        ExecucaoCurva f4 = criarFilha(mae.id(), conjunto, LocalDate.of(2023, 10, 5));
        f4.iniciarExecucao();
        execucaoRepository.atualizar(f4);

        // 5. Calcular Progresso
        ProgressoBackfill progresso = backfillRepository.calcularProgresso(mae.id());
        
        assertThat(progresso.total()).isEqualTo(4);
        assertThat(progresso.concluidas()).isEqualTo(1);
        assertThat(progresso.semDado()).isEqualTo(1);
        assertThat(progresso.falhas()).isEqualTo(1);
        assertThat(progresso.pendentes()).isEqualTo(1);
    }

    private ExecucaoCurva criarFilha(UUID maeId, String conjunto, LocalDate data) {
        ExecucaoCurva filha = ExecucaoCurva.iniciar(
                UUID.randomUUID(), maeId, null, conjunto, data,
                MomentoCurva.INTRADIA, TipoDisparo.BACKFILL, "teste", Faixa.MASSA, null, null
        );
        execucaoRepository.inserir(filha);
        return filha;
    }
}
