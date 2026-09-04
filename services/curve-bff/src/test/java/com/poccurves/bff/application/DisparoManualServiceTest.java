package com.poccurves.bff.application;

import com.poccurves.bff.dto.BffDtos.BackfillRequest;
import com.poccurves.bff.dto.BffDtos.BackfillResponse;
import com.poccurves.bff.dto.BffDtos.DisparoManualRequest;
import com.poccurves.bff.dto.BffDtos.DisparoManualResponse;
import com.poccurves.bff.dto.BffDtos.ProgressoConjuntoDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.web.client.HttpServerErrorException;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DisparoManualServiceTest {

    private CurveOrchestratorPort orchestratorClient;
    private DisparoManualService service;

    @BeforeEach
    void setUp() {
        orchestratorClient = Mockito.mock(CurveOrchestratorPort.class);
        service = new DisparoManualService(orchestratorClient);
    }

    @Test
    void deveRecusarDisparoEmFinalDeSemanaSemChamarOrquestrador() {
        // 2026-08-23 é Domingo
        LocalDate domingo = LocalDate.of(2026, 8, 23);
        DisparoManualRequest request = new DisparoManualRequest(
                domingo, List.of("BVBG_086"), "Disparo de teste em domingo"
        );

        DisparoManualResponse response = service.disparoManual(request);

        assertThat(response.status()).isEqualTo("RECUSADO_NAO_PREGAO");
        assertThat(response.mensagem()).contains("não é dia útil de pregão");
        verifyNoInteractions(orchestratorClient);
    }

    /**
     * Tarefa 8.7 (auditoria desta sessão): dois conjuntos de insumo no
     * mesmo disparo — a faixa prioritária processa os dois, cada um com seu
     * próprio progresso reportado pelo orquestrador.
     */
    @Test
    void deveDelegarDisparoComDoisConjuntosDeInsumoAoOrquestradorSemAlterarAResposta() {
        LocalDate diaUtil = LocalDate.of(2026, 8, 21); // sexta-feira
        DisparoManualRequest request = new DisparoManualRequest(
                diaUtil, List.of("BVBG_086", "BVBG_028"), "Disparo de teste com dois insumos"
        );
        DisparoManualResponse respostaReal = new DisparoManualResponse(
                "corr-real-123",
                "DISPARADO",
                "Disparo aceito pelo orquestrador.",
                List.of(
                        new ProgressoConjuntoDTO("BVBG_086", "INICIADO", "na fila"),
                        new ProgressoConjuntoDTO("BVBG_028", "INICIADO", "na fila")
                )
        );
        when(orchestratorClient.disparoManual(request)).thenReturn(respostaReal);

        DisparoManualResponse resposta = service.disparoManual(request);

        // A resposta do serviço é EXATAMENTE a do orquestrador — nenhum dado
        // fabricado pelo BFF (diferente do comportamento encontrado na
        // auditoria: um UUID/status/progresso inventados quando a chamada falhava).
        assertThat(resposta).isSameAs(respostaReal);
        assertThat(resposta.progressoPorConjunto()).hasSize(2);
    }

    /**
     * Tarefa 8.8 (parcial, auditoria desta sessão): "execução já em
     * andamento" é um status que o ORQUESTRADOR decide e retorna — o BFF só
     * repassa, nunca decide isso sozinho.
     */
    @Test
    void devePassarAdianteOStatusJaEmAndamentoRetornadoPeloOrquestrador() {
        LocalDate diaUtil = LocalDate.of(2026, 8, 21);
        DisparoManualRequest request = new DisparoManualRequest(diaUtil, List.of("BVBG_086"), "teste");
        DisparoManualResponse respostaJaEmAndamento = new DisparoManualResponse(
                "corr-existente-456", "JA_EM_ANDAMENTO", "Já existe uma execução em andamento para esta data.", List.of());
        when(orchestratorClient.disparoManual(request)).thenReturn(respostaJaEmAndamento);

        DisparoManualResponse resposta = service.disparoManual(request);

        assertThat(resposta.status()).isEqualTo("JA_EM_ANDAMENTO");
    }

    /**
     * Tarefa 8.21 (auditoria desta sessão): corrige o bug real encontrado —
     * antes, qualquer falha do orquestrador (rede, 404, timeout) era
     * engolida e o serviço fabricava uma resposta "DISPARADO" falsa, com
     * UUID e progresso inventados. Agora a falha propaga de verdade.
     */
    @Test
    void devePropagarFalhaQuandoOrquestradorNaoResponde() {
        LocalDate diaUtil = LocalDate.of(2026, 8, 21);
        DisparoManualRequest request = new DisparoManualRequest(diaUtil, List.of("BVBG_086"), "teste");
        when(orchestratorClient.disparoManual(any()))
                .thenThrow(HttpServerErrorException.create(
                        org.springframework.http.HttpStatus.NOT_FOUND, "Not Found", null, null, null));

        assertThatThrownBy(() -> service.disparoManual(request))
                .isInstanceOf(HttpServerErrorException.class);
    }

    @Test
    void deveDelegarBackfillAoOrquestradorSemAlterarAResposta() {
        BackfillRequest request = new BackfillRequest(
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 21), List.of("PRE"), "backfill de teste");
        BackfillResponse respostaReal = new BackfillResponse("corr-backfill-1", 15, "DISPARADO", "Backfill real aceito.");
        when(orchestratorClient.backfill(request)).thenReturn(respostaReal);

        BackfillResponse resposta = service.backfill(request);

        assertThat(resposta).isSameAs(respostaReal);
    }

    @Test
    void devePropagarFalhaDeBackfillQuandoOrquestradorNaoResponde() {
        BackfillRequest request = new BackfillRequest(
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 21), List.of("PRE"), "teste");
        when(orchestratorClient.backfill(any()))
                .thenThrow(HttpServerErrorException.create(
                        org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, "Unavailable", null, null, null));

        assertThatThrownBy(() -> service.backfill(request))
                .isInstanceOf(HttpServerErrorException.class);
    }
}
