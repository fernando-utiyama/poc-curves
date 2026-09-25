package com.poccurves.api.application;

import com.poccurves.api.domain.PontoCurva;
import com.poccurves.api.dto.ApiDtos.ComparacaoCurvasRequest;
import com.poccurves.api.dto.ApiDtos.ComparacaoCurvasResponse;
import com.poccurves.api.dto.ApiDtos.CurvaDadosResponse;
import com.poccurves.api.dto.ApiDtos.ItemComparacaoCurvasDTO;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CurvaDadosServiceTest {

    @Mock
    private CurvaDadosRepositoryPort repository;

    private CurvaDadosService service;

    private final String ticker = "B3_TAXA_SWAP_DCL";
    private final LocalDate dataRef = LocalDate.of(2026, 9, 14);

    @BeforeEach
    void setup() {
        service = new CurvaDadosService(repository);
    }

    @Test
    void consultarVerticesDelegaParaBuscarVertices() {
        when(repository.buscarVertices(ticker, dataRef)).thenReturn(List.of(
                new PontoCurva(LocalDate.of(2026, 9, 15), new BigDecimal("13.90")),
                new PontoCurva(LocalDate.of(2026, 10, 14), new BigDecimal("13.85"))
        ));

        CurvaDadosResponse resposta = service.consultarVertices(ticker, dataRef);

        assertThat(resposta.tickerIndcd()).isEqualTo(ticker);
        assertThat(resposta.dataReferencia()).isEqualTo(dataRef);
        assertThat(resposta.pontos()).hasSize(2);
        assertThat(resposta.pontos().get(0).dataVertice()).isEqualTo(LocalDate.of(2026, 9, 15));
        assertThat(resposta.pontos().get(0).valor()).isEqualByComparingTo("13.90");
    }

    @Test
    void consultarVerticesLancaExcecaoQuandoNaoHaDados() {
        when(repository.buscarVertices(ticker, dataRef)).thenReturn(List.of());

        assertThatThrownBy(() -> service.consultarVertices(ticker, dataRef))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining(ticker);
    }

    @Test
    void consultarCurvaConstruidaDelegaParaBuscarCurvaConstruida() {
        when(repository.buscarCurvaConstruida(ticker, dataRef)).thenReturn(List.of(
                new PontoCurva(LocalDate.of(2026, 9, 15), new BigDecimal("13.90"))
        ));

        CurvaDadosResponse resposta = service.consultarCurvaConstruida(ticker, dataRef);

        assertThat(resposta.pontos()).hasSize(1);
        assertThat(resposta.pontos().get(0).valor()).isEqualByComparingTo("13.90");
    }

    @Test
    void consultarCurvaConstruidaLancaExcecaoQuandoNaoHaDados() {
        when(repository.buscarCurvaConstruida(ticker, dataRef)).thenReturn(List.of());

        assertThatThrownBy(() -> service.consultarCurvaConstruida(ticker, dataRef))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining(ticker);
    }

    @Test
    void compararCurvasMesclaPorDataComAmbosOsLadosPresentes() {
        String tickerA = "B3_TAXA_SWAP_DCL";
        String tickerB = "B3_TAXA_SWAP_PRE";
        when(repository.buscarCurvaConstruida(tickerA, dataRef)).thenReturn(List.of(
                new PontoCurva(LocalDate.of(2026, 9, 15), new BigDecimal("13.90")),
                new PontoCurva(LocalDate.of(2026, 10, 14), new BigDecimal("13.85"))
        ));
        when(repository.buscarCurvaConstruida(tickerB, dataRef)).thenReturn(List.of(
                new PontoCurva(LocalDate.of(2026, 9, 15), new BigDecimal("14.00")),
                new PontoCurva(LocalDate.of(2026, 10, 14), new BigDecimal("14.10"))
        ));

        ComparacaoCurvasResponse resposta = service.compararCurvas(
                new ComparacaoCurvasRequest(dataRef, tickerA, tickerB));

        assertThat(resposta.pontos()).hasSize(2);
        assertThat(resposta.pontos().get(0).valorA()).isEqualByComparingTo("13.90");
        assertThat(resposta.pontos().get(0).valorB()).isEqualByComparingTo("14.00");
    }

    @Test
    void compararCurvasMarcaComNuloOLadoAusenteQuandoDataExisteEmApenasUmaCurva() {
        String tickerA = "B3_TAXA_SWAP_DCL";
        String tickerB = "B3_TAXA_SWAP_PRE";
        when(repository.buscarCurvaConstruida(tickerA, dataRef)).thenReturn(List.of(
                new PontoCurva(LocalDate.of(2026, 9, 15), new BigDecimal("13.90")),
                new PontoCurva(LocalDate.of(2026, 11, 16), new BigDecimal("13.95")) // exclusivo de A
        ));
        when(repository.buscarCurvaConstruida(tickerB, dataRef)).thenReturn(List.of(
                new PontoCurva(LocalDate.of(2026, 9, 15), new BigDecimal("14.00")),
                new PontoCurva(LocalDate.of(2026, 12, 14), new BigDecimal("14.20")) // exclusivo de B
        ));

        ComparacaoCurvasResponse resposta = service.compararCurvas(
                new ComparacaoCurvasRequest(dataRef, tickerA, tickerB));

        assertThat(resposta.pontos()).hasSize(3);
        assertThat(resposta.pontos()).extracting(ItemComparacaoCurvasDTO::dataVertice)
                .containsExactly(
                        LocalDate.of(2026, 9, 15),
                        LocalDate.of(2026, 11, 16),
                        LocalDate.of(2026, 12, 14));

        ItemComparacaoCurvasDTO exclusivoA = resposta.pontos().get(1);
        assertThat(exclusivoA.valorA()).isEqualByComparingTo("13.95");
        assertThat(exclusivoA.valorB()).isNull();

        ItemComparacaoCurvasDTO exclusivoB = resposta.pontos().get(2);
        assertThat(exclusivoB.valorA()).isNull();
        assertThat(exclusivoB.valorB()).isEqualByComparingTo("14.20");
    }

    @Test
    void compararCurvasLancaExcecaoQuandoAmbosOsLadosEstaoVazios() {
        String tickerA = "B3_TAXA_SWAP_DCL";
        String tickerB = "B3_TAXA_SWAP_PRE";
        when(repository.buscarCurvaConstruida(tickerA, dataRef)).thenReturn(List.of());
        when(repository.buscarCurvaConstruida(tickerB, dataRef)).thenReturn(List.of());

        assertThatThrownBy(() -> service.compararCurvas(new ComparacaoCurvasRequest(dataRef, tickerA, tickerB)))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining(tickerA)
                .hasMessageContaining(tickerB);
    }
}
