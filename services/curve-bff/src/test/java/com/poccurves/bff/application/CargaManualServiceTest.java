package com.poccurves.bff.application;

import com.poccurves.bff.dto.BffDtos.CargaManualResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.LocalDate;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

class CargaManualServiceTest {

    private CurveOrchestratorPort orchestratorClient;
    private CargaManualService service;

    @BeforeEach
    void setUp() {
        orchestratorClient = Mockito.mock(CurveOrchestratorPort.class);
        service = new CargaManualService(orchestratorClient);
    }

    @Test
    void deveRecusarArquivoSemJustificativa() {
        byte[] bytes = "prazo,taxa".getBytes();

        assertThatThrownBy(() -> service.carregarCurva("PRE", LocalDate.now(), "FECHAMENTO", "", bytes, "curva.csv"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Justificativa é obrigatória");
    }

    @Test
    void deveRecusarFormatoNaoCsvNemXlsx() {
        byte[] bytes = "dummy".getBytes();

        assertThatThrownBy(() -> service.carregarCurva("PRE", LocalDate.now(), "FECHAMENTO", "Justificativa teste", bytes, "curva.exe"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Formato de arquivo inválido");
    }

    @Test
    void deveEncaminharBytesDoArquivoSemParsearConteudoInterno() {
        byte[] payload = "prazo_dias_uteis,taxa\n21,14.129\n".getBytes();

        when(orchestratorClient.cargaManualCurva(eq("PRE"), any(), eq("FECHAMENTO"), eq("Carga contingência"), eq(payload), eq("curva_pre.csv")))
                .thenReturn(new CargaManualResponse("corr-999", "ACEITA", "Arquivo aceito", Collections.emptyList(), Collections.emptyList()));

        CargaManualResponse resp = service.carregarCurva("PRE", LocalDate.now(), "FECHAMENTO", "Carga contingência", payload, "curva_pre.csv");

        assertThat(resp.status()).isEqualTo("ACEITA");
        assertThat(resp.correlationId()).isEqualTo("corr-999");
    }
}
