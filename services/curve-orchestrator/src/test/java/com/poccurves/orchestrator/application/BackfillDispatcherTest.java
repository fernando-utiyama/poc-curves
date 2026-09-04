package com.poccurves.orchestrator.application;

import com.poccurves.orchestrator.application.AquisicaoExecutionService.ExecutionResult;
import com.poccurves.orchestrator.application.FeederAcquisitionPort.ResultadoAquisicao;
import com.poccurves.orchestrator.domain.ExecucaoCurva;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class BackfillDispatcherTest {

    private ExecutorService backfillExecutor;
    private ExecucaoCurvaRepositoryPort execucaoCurvaRepository;
    private BackfillRepositoryPort backfillRepository;
    private AquisicaoExecutionService aquisicaoExecutionService;
    private BackfillDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        backfillExecutor = Executors.newFixedThreadPool(4);
        execucaoCurvaRepository = mock(ExecucaoCurvaRepositoryPort.class);
        backfillRepository = mock(BackfillRepositoryPort.class);
        aquisicaoExecutionService = mock(AquisicaoExecutionService.class);
        dispatcher = new BackfillDispatcher(backfillExecutor, execucaoCurvaRepository, backfillRepository, aquisicaoExecutionService);
    }

    @AfterEach
    void tearDown() {
        backfillExecutor.shutdownNow();
    }

    @Test
    void despachaCriandoUmaFilhaPorDiaDePregaoEPulandoFinsDeSemana() throws InterruptedException {
        UUID maeId = UUID.randomUUID();
        // 2023-10-06 (sexta), 07 (sab), 08 (dom), 09 (seg), 10 (ter) -> 3 dias úteis
        LocalDate start = LocalDate.of(2023, 10, 6);
        LocalDate end = LocalDate.of(2023, 10, 10);

        ResultadoAquisicao rPublished = new ResultadoAquisicao("PUBLISHED", "lote1", 1, null, null);
        when(aquisicaoExecutionService.acionarFeederEEncadear(any(), any(), any(), any(), any()))
                .thenReturn(new ExecutionResult(rPublished, "EM_PROCESSAMENTO", "msg", false));
        when(backfillRepository.interrupcaoSolicitada(maeId)).thenReturn(false);

        ExecucaoCurva mae = mock(ExecucaoCurva.class);
        when(execucaoCurvaRepository.buscarPorId(maeId)).thenReturn(Optional.of(mae));

        // CalendarioPregao real, sem mock: Mockito.mockStatic() é thread-confinado (ThreadLocal) e
        // BackfillDispatcher despacha em threads do próprio backfillExecutor, não na thread do teste —
        // o mock nunca seria visto lá (foi isso que causou "zero interactions" quando o agy tentou
        // mockar, encontrado na auditoria desta sessão). 2023-10-06/09/10 são dias úteis reais sem
        // feriado nacional nesse intervalo, e 07/08 é sábado/domingo real — o calendário real já dá
        // exatamente os "3 dias úteis" que o teste espera, sem precisar mockar nada.
        dispatcher.despachar(maeId, "CONJUNTO", start, end, 2, UUID.randomUUID());

        // despachar() é fire-and-forget (só faz um submit() e retorna) -- não dá pra chamar
        // backfillExecutor.shutdown() logo em seguida: a tarefa de despacho, rodando em background,
        // ainda vai chamar backfillExecutor.submit() pra cada filha, e um executor já em shutdown()
        // rejeita submit() novo (RejectedExecutionException, não capturada, silenciosamente engolida
        // pelo ExecutorService) -- foi exatamente essa corrida que causava "zero interactions" mesmo
        // depois de tirar o mockStatic, encontrado na auditoria desta sessão. verify(..., timeout(...))
        // espera a condição ficar verdadeira em vez de arriscar essa corrida.
        verify(aquisicaoExecutionService, timeout(5000).times(3)).acionarFeederEEncadear(any(), any(), any(), any(), any());
        verify(execucaoCurvaRepository, timeout(5000).times(3)).inserir(any(ExecucaoCurva.class));
        verify(mae, timeout(5000)).iniciarConstrucao();
        verify(mae, timeout(5000)).concluir();
        verify(execucaoCurvaRepository, timeout(5000)).atualizar(mae);
    }

    @Test
    void paraDeCriarFilhasQuandoInterrupcaoSolicitadaRetornaTrue() throws InterruptedException {
        UUID maeId = UUID.randomUUID();
        LocalDate start = LocalDate.of(2023, 10, 2);
        LocalDate end = LocalDate.of(2023, 10, 6); // 5 dias uteis

        ResultadoAquisicao rPublished = new ResultadoAquisicao("PUBLISHED", "lote1", 1, null, null);
        when(aquisicaoExecutionService.acionarFeederEEncadear(any(), any(), any(), any(), any()))
                .thenReturn(new ExecutionResult(rPublished, "EM_PROCESSAMENTO", "msg", false));

        // Retorna false na 1a vez (dia 2), false na 2a vez (dia 3), true na 3a vez (dia 4)
        when(backfillRepository.interrupcaoSolicitada(maeId))
                .thenReturn(false)
                .thenReturn(false)
                .thenReturn(true);

        ExecucaoCurva mae = mock(ExecucaoCurva.class);
        when(execucaoCurvaRepository.buscarPorId(maeId)).thenReturn(Optional.of(mae));

        // CalendarioPregao real (ver comentário no teste anterior) — 2023-10-02 a 06 são
        // segunda a sexta reais, sem feriado nacional no intervalo, então já são os "5 dias úteis".
        dispatcher.despachar(maeId, "CONJUNTO", start, end, 5, UUID.randomUUID());

        // Mãe finalizada (mesmo interrompida, o dispatcher termina e fecha) -- esperar por isso primeiro
        // garante que o loop de despacho já terminou de decidir quantos dias processar antes de
        // conferir o total abaixo (ver comentário do teste anterior sobre por que não dá pra usar
        // shutdown()+awaitTermination() aqui).
        verify(mae, timeout(5000)).iniciarConstrucao();
        verify(mae, timeout(5000)).concluir();

        // Só processou 2 dias e parou
        verify(aquisicaoExecutionService, times(2)).acionarFeederEEncadear(any(), any(), any(), any(), any());
        verify(execucaoCurvaRepository, times(2)).inserir(any(ExecucaoCurva.class));
    }
}
