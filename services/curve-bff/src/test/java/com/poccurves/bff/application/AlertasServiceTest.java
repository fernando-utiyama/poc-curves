package com.poccurves.bff.application;

import com.poccurves.bff.dto.BffDtos.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class AlertasServiceTest {

    private CurveOrchestratorPort orchestratorClient;
    private PainelDoDiaService painelDoDiaService;
    private AlertasService service;

    @BeforeEach
    void setUp() {
        orchestratorClient = Mockito.mock(CurveOrchestratorPort.class);
        painelDoDiaService = Mockito.mock(PainelDoDiaService.class);
        service = new AlertasService(orchestratorClient, painelDoDiaService);
    }

    @Test
    void deveCalcularAlertaLeveComSeveridadeAltaQuandoPendenciaMaisAntigaPassarDeUmaHora() {
        Instant umaHoraEMeiaAtras = Instant.now().minus(90, ChronoUnit.MINUTES);

        GrupoPendenciaDTO grupo = new GrupoPendenciaDTO(
                UUID.randomUUID(), "ERRO_SCHEMA", "B3", "BVBG_086", LocalDate.now(), 5,
                umaHoraEMeiaAtras, Instant.now(), "Campo ausente", "ABERTO"
        );

        when(orchestratorClient.getPendenciasSumario())
                .thenReturn(new PendenciasSumarioResponse(List.of(grupo), 1, 5));

        when(painelDoDiaService.obterPainelDoDia(any()))
                .thenReturn(new PainelDoDiaResponse(LocalDate.now(), Collections.emptyList()));

        AlertasSumarioResponse resp = service.obterAlertasSumario();

        assertThat(resp.gruposPendenciasAbertas()).isEqualTo(1);
        assertThat(resp.totalMensagensPendentes()).isEqualTo(5);
        assertThat(resp.severidadePendencias()).isEqualTo("ALTA");
        assertThat(resp.idadeMaisAntigaMinutos()).isGreaterThanOrEqualTo(89);
    }

    /** Tarefa 8.13 (parcial, auditoria desta sessão): sem nenhuma pendência DLQ aberta. */
    @Test
    void deveRetornarSeveridadeBaixaESemIdadeQuandoNaoHaPendencias() {
        when(orchestratorClient.getPendenciasSumario())
                .thenReturn(new PendenciasSumarioResponse(Collections.emptyList(), 0, 0));
        when(painelDoDiaService.obterPainelDoDia(any()))
                .thenReturn(new PainelDoDiaResponse(LocalDate.now(), Collections.emptyList()));

        AlertasSumarioResponse resp = service.obterAlertasSumario();

        assertThat(resp.gruposPendenciasAbertas()).isEqualTo(0);
        assertThat(resp.totalMensagensPendentes()).isEqualTo(0);
        assertThat(resp.severidadePendencias()).isEqualTo("BAIXA");
        assertThat(resp.idadeMaisAntigaMinutos()).isNull();
    }

    /**
     * Tarefa 8.20 (auditoria desta sessão): curva em risco/atrasada sem
     * nenhuma pendência DLQ — as duas fontes (DLQ e painel do dia) são
     * independentes, o alerta tem que refletir o painel mesmo com DLQ vazia.
     */
    @Test
    void deveContarCurvasEmRiscoEAtrasadasMesmoSemNenhumaPendenciaDlq() {
        when(orchestratorClient.getPendenciasSumario())
                .thenReturn(new PendenciasSumarioResponse(Collections.emptyList(), 0, 0));

        ItemPainelDoDiaDTO emRisco = new ItemPainelDoDiaDTO(
                "PRE", "Curva Pré", "BRL", "BOOTSTRAPPED", "EM_RISCO", "18:00",
                12, 5, "CONSTRUCAO", 3, null, 0, null);
        ItemPainelDoDiaDTO atrasada = new ItemPainelDoDiaDTO(
                "CDI", "Curva CDI", "BRL", "IMPORTED", "ATRASADA", "18:00",
                -5, -5, "PUBLICACAO", 2, null, 1, null);
        ItemPainelDoDiaDTO publicada = new ItemPainelDoDiaDTO(
                "IPCA", "Curva IPCA", "BRL", "IMPORTED", "PUBLICADA", "18:00",
                null, null, null, 4, null, 0, Instant.now());

        when(painelDoDiaService.obterPainelDoDia(any()))
                .thenReturn(new PainelDoDiaResponse(LocalDate.now(), List.of(emRisco, atrasada, publicada)));

        AlertasSumarioResponse resp = service.obterAlertasSumario();

        assertThat(resp.gruposPendenciasAbertas()).isEqualTo(0);
        assertThat(resp.curvasEmRiscoContagem()).isEqualTo(1);
        assertThat(resp.curvasAtrasadasContagem()).isEqualTo(1);
        assertThat(resp.menorMargemMinutos()).isEqualTo(-5);
    }

    @Test
    void deveClassificarSeveridadeCriticaQuandoPendenciaMaisAntigaPassarDeQuatroHoras() {
        Instant cincoHorasAtras = Instant.now().minus(300, ChronoUnit.MINUTES);
        GrupoPendenciaDTO grupo = new GrupoPendenciaDTO(
                UUID.randomUUID(), "ERRO_SCHEMA", "B3", "BVBG_086", LocalDate.now(), 20,
                cincoHorasAtras, Instant.now(), "Campo ausente", "ABERTO");

        when(orchestratorClient.getPendenciasSumario())
                .thenReturn(new PendenciasSumarioResponse(List.of(grupo), 1, 20));
        when(painelDoDiaService.obterPainelDoDia(any()))
                .thenReturn(new PainelDoDiaResponse(LocalDate.now(), Collections.emptyList()));

        AlertasSumarioResponse resp = service.obterAlertasSumario();

        assertThat(resp.severidadePendencias()).isEqualTo("CRITICA");
    }
}
