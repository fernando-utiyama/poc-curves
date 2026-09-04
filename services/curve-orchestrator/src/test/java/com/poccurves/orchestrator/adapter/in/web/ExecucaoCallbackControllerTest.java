package com.poccurves.orchestrator.adapter.in.web;

import com.poccurves.orchestrator.application.ExecucaoCurvaRepositoryPort;
import com.poccurves.orchestrator.domain.EstadoExecucao;
import com.poccurves.orchestrator.domain.ExecucaoCurva;
import com.poccurves.orchestrator.domain.Faixa;
import com.poccurves.orchestrator.domain.MomentoCurva;
import com.poccurves.orchestrator.domain.TipoDisparo;
import com.poccurves.orchestrator.dto.OrchestratorDtos.ConclusaoConstrucaoRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExecucaoCallbackControllerTest {

    private ExecucaoCurvaRepositoryPort repository;
    private ExecucaoCallbackController controller;

    @BeforeEach
    void setup() {
        repository = mock(ExecucaoCurvaRepositoryPort.class);
        controller = new ExecucaoCallbackController(repository);
    }

    private ExecucaoCurva criarExecucaoEmConstruindo() {
        ExecucaoCurva execucao = ExecucaoCurva.iniciar(
                UUID.randomUUID(), null, null, "BVBG.086", LocalDate.of(2026, 8, 25),
                MomentoCurva.FECHAMENTO, TipoDisparo.AGENDADO, "sistema",
                Faixa.ROTINA, null, null);
        execucao.iniciarExecucao();
        execucao.iniciarConstrucao();
        return execucao;
    }

    @Test
    void callbackComExecutionIdInexistenteRetorna404() {
        UUID executionId = UUID.randomUUID();
        when(repository.buscarPorId(executionId)).thenReturn(Optional.empty());

        ResponseEntity<Void> response = controller.callbackConcluida(
                executionId, new ConclusaoConstrucaoRequest("PUBLICADA", null));

        assertThat(response.getStatusCode().value()).isEqualTo(404);
        verify(repository, never()).atualizar(any());
    }

    @Test
    void callbackComStatusPublicadaSobreExecucaoConstruindoConcluiEAtualiza() {
        ExecucaoCurva execucao = criarExecucaoEmConstruindo();
        UUID executionId = execucao.id();
        when(repository.buscarPorId(executionId)).thenReturn(Optional.of(execucao));

        ResponseEntity<Void> response = controller.callbackConcluida(
                executionId, new ConclusaoConstrucaoRequest("PUBLICADA", null));

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(execucao.estado()).isEqualTo(EstadoExecucao.CONCLUIDA);
        verify(repository).atualizar(execucao);
    }

    @Test
    void callbackComStatusErroSobreExecucaoConstruindoFalhaEAtualiza() {
        ExecucaoCurva execucao = criarExecucaoEmConstruindo();
        UUID executionId = execucao.id();
        when(repository.buscarPorId(executionId)).thenReturn(Optional.of(execucao));

        ResponseEntity<Void> response = controller.callbackConcluida(
                executionId, new ConclusaoConstrucaoRequest("ERRO", "reprovado na suavidade"));

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(execucao.estado()).isEqualTo(EstadoExecucao.FALHOU);
        assertThat(execucao.codigoErro()).isEqualTo("FALHA_CONSTRUCAO_ENGINE");
        assertThat(execucao.mensagemErro()).isEqualTo("reprovado na suavidade");
        verify(repository).atualizar(execucao);
    }

    @Test
    void callbackDuplicadoSobreExecucaoJaConcluidaEhIdempotenteRetorna200SemErro() {
        ExecucaoCurva execucao = criarExecucaoEmConstruindo();
        execucao.concluir();
        assertThat(execucao.estado()).isEqualTo(EstadoExecucao.CONCLUIDA);

        UUID executionId = execucao.id();
        when(repository.buscarPorId(executionId)).thenReturn(Optional.of(execucao));

        ResponseEntity<Void> response = controller.callbackConcluida(
                executionId, new ConclusaoConstrucaoRequest("PUBLICADA", null));

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(execucao.estado()).isEqualTo(EstadoExecucao.CONCLUIDA);
        verify(repository, never()).atualizar(any());
    }
}
