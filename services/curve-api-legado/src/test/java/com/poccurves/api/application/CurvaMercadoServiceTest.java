package com.poccurves.api.application;

import com.poccurves.api.domain.CurvaMercado;
import com.poccurves.api.dto.ApiDtos.CatalogoCurvasResponse;
import com.poccurves.api.dto.ApiDtos.CurvaMercadoResponse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CurvaMercadoServiceTest {

    @Mock
    private CurvaMercadoRepositoryPort repository;

    private CurvaMercadoService service;

    @BeforeEach
    void setup() {
        service = new CurvaMercadoService(repository);
    }

    @Test
    void listarTodasDelegaParaORepositorioEConverteParaResponse() {
        CurvaMercado dcl = new CurvaMercado("B3_TAXA_SWAP_DCL", "CURVA_SWAP", "TAXA_JUROS", "BRL",
                LocalDate.of(2020, 1, 1), "sistema");
        CurvaMercado pre = new CurvaMercado("B3_TAXA_SWAP_PRE", "CURVA_SWAP", "TAXA_JUROS", "BRL",
                LocalDate.of(2020, 1, 1), "sistema");
        when(repository.listarTodas()).thenReturn(List.of(dcl, pre));

        CatalogoCurvasResponse resposta = service.listarTodas();

        assertThat(resposta.curvas()).hasSize(2);
        assertThat(resposta.curvas()).extracting(CurvaMercadoResponse::tickerIndcd)
                .containsExactly("B3_TAXA_SWAP_DCL", "B3_TAXA_SWAP_PRE");
        assertThat(resposta.curvas().get(0).moedaNegoc()).isEqualTo("BRL");
    }

    @Test
    void obterPorTickerRetornaAResponseQuandoEncontrado() {
        CurvaMercado curva = new CurvaMercado("B3_TAXA_SWAP_PTX", "CURVA_SWAP", "TAXA_JUROS", "BRL",
                LocalDate.of(2020, 1, 1), "sistema");
        when(repository.buscarPorTicker("B3_TAXA_SWAP_PTX")).thenReturn(Optional.of(curva));

        CurvaMercadoResponse resposta = service.obterPorTicker("B3_TAXA_SWAP_PTX");

        assertThat(resposta.tickerIndcd()).isEqualTo("B3_TAXA_SWAP_PTX");
        assertThat(resposta.classfInstt()).isEqualTo("CURVA_SWAP");
        assertThat(resposta.inicVigencia()).isEqualTo(LocalDate.of(2020, 1, 1));
    }

    @Test
    void obterPorTickerLancaExcecaoQuandoNaoEncontrado() {
        when(repository.buscarPorTicker("TICKER_INEXISTENTE")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.obterPorTicker("TICKER_INEXISTENTE"))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("TICKER_INEXISTENTE");
    }
}
