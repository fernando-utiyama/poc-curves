package com.poccurves.orchestrator.application;

import com.poccurves.orchestrator.domain.ExecucaoCurva;
import com.poccurves.orchestrator.domain.ProgressoBackfill;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class BackfillServiceTest {

    private ExecucaoCurvaRepositoryPort execucaoCurvaRepository;
    private BackfillRepositoryPort backfillRepository;
    private BackfillDispatcher backfillDispatcher;
    private BackfillService service;

    @BeforeEach
    void setUp() {
        execucaoCurvaRepository = mock(ExecucaoCurvaRepositoryPort.class);
        backfillRepository = mock(BackfillRepositoryPort.class);
        backfillDispatcher = mock(BackfillDispatcher.class);
        service = new BackfillService(execucaoCurvaRepository, backfillRepository, backfillDispatcher, 5);
    }

    @Test
    void iniciarRejeitaDataInicialMaiorQueFinal() {
        assertThatThrownBy(() -> service.iniciar("CONJUNTO", LocalDate.of(2023, 10, 5), LocalDate.of(2023, 10, 4), null, "teste"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("dataInicial não pode ser posterior a dataFinal");
    }

    @Test
    void iniciarCriaExecucaoEDisparaDispatcherComConcorrenciaPadrao() {
        LocalDate start = LocalDate.of(2023, 10, 1);
        LocalDate end = LocalDate.of(2023, 10, 5);

        ExecucaoCurva mae = service.iniciar("CONJUNTO", start, end, null, "teste");

        assertThat(mae).isNotNull();
        assertThat(mae.estado().name()).isEqualTo("EXECUTANDO"); // transicionou pra EXECUTANDO

        ArgumentCaptor<ExecucaoCurva> captor = ArgumentCaptor.forClass(ExecucaoCurva.class);
        verify(execucaoCurvaRepository).inserir(captor.capture());
        verify(execucaoCurvaRepository).atualizar(any(ExecucaoCurva.class));
        assertThat(captor.getValue().id()).isEqualTo(mae.id());

        verify(backfillRepository).inserirBackfillExecucao(mae.id(), end, 5);

        verify(backfillDispatcher).despachar(eq(mae.id()), eq("CONJUNTO"), eq(start), eq(end), eq(5), any(UUID.class));
    }

    @Test
    void iniciarUsaConcorrenciaInformadaSeMaiorQueZero() {
        LocalDate start = LocalDate.of(2023, 10, 1);
        LocalDate end = LocalDate.of(2023, 10, 5);

        ExecucaoCurva mae = service.iniciar("CONJUNTO", start, end, 10, "teste");

        verify(backfillRepository).inserirBackfillExecucao(mae.id(), end, 10);
        verify(backfillDispatcher).despachar(eq(mae.id()), eq("CONJUNTO"), eq(start), eq(end), eq(10), any(UUID.class));
    }

    @Test
    void interromperDelegaParaRepository() {
        UUID id = UUID.randomUUID();
        service.interromper(id);
        verify(backfillRepository).solicitarInterrupcao(id);
    }

    @Test
    void progressoDelegaParaRepository() {
        UUID id = UUID.randomUUID();
        ProgressoBackfill progresso = new ProgressoBackfill(10, 5, 2, 1, 2);
        when(backfillRepository.calcularProgresso(id)).thenReturn(progresso);

        ProgressoBackfill result = service.progresso(id);

        assertThat(result).isEqualTo(progresso);
        verify(backfillRepository).calcularProgresso(id);
    }
}
