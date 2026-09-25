package com.poccurves.engine.application.service;
import com.poccurves.engine.adapter.out.construcao.BuiltinModeloConstrucao;
import com.poccurves.engine.application.model.ModeloCurva;
import com.poccurves.engine.application.model.VerticeBtrs;
import com.poccurves.engine.application.model.VerticeConstruido;
import com.poccurves.engine.application.port.BtrsCurvaPrimrConsultaRepositoryPort;
import com.poccurves.engine.application.port.ConfgCurvaRepositoryPort;
import com.poccurves.engine.application.port.CurvaDataRepositoryPort;
import com.poccurves.engine.application.port.DadoCurvaRepositoryPort;
import com.poccurves.engine.application.port.ModeloCurvaRepositoryPort;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConstrucaoCurvaB3ServiceTest {

    @Mock private BtrsCurvaPrimrConsultaRepositoryPort btrsCurvaPrimrRepository;
    @Mock private ConfgCurvaRepositoryPort confgCurvaRepository;
    @Mock private ModeloCurvaRepositoryPort modeloCurvaRepository;
    @Mock private DadoCurvaRepositoryPort dadoCurvaRepository;
    @Mock private CurvaDataRepositoryPort curvaDataRepository;

    // Resolver real com o modelo BUILTIN real (só transcreve, sem I/O) — cobre a integração
    // real entre ConstrucaoCurvaB3Service e ModeloConstrucaoResolver, não só o mock.
    private final ModeloConstrucaoResolver modeloConstrucaoResolver =
            new ModeloConstrucaoResolver(List.of(new BuiltinModeloConstrucao()));

    private ConstrucaoCurvaB3Service service;

    private final String ticker = "B3_TAXA_SWAP_DCL";
    private final LocalDate dataRef = LocalDate.of(2026, 9, 14);
    private static final String CODIGO_MODELO = BuiltinModeloConstrucao.CODIGO_TAXA_SWAP_TRANSCRICAO_B3;

    @BeforeEach
    void setup() {
        service = new ConstrucaoCurvaB3Service(
                btrsCurvaPrimrRepository, confgCurvaRepository, modeloCurvaRepository,
                modeloConstrucaoResolver, dadoCurvaRepository, curvaDataRepository);
    }

    @Test
    void constroiVerticesEEspelhaEmTCurvaDataComDataResolvidaPorDiasCorridos() {
        when(btrsCurvaPrimrRepository.buscarVertices(ticker, dataRef)).thenReturn(List.of(
                new VerticeBtrs(1, 1, new BigDecimal("13.90")),
                new VerticeBtrs(30, 21, new BigDecimal("13.85"))
        ));
        when(confgCurvaRepository.buscarMotorCalcVigente(ticker, dataRef)).thenReturn(Optional.of(CODIGO_MODELO));
        when(modeloCurvaRepository.buscarPorCodigo(CODIGO_MODELO))
                .thenReturn(Optional.of(ModeloCurva.builtin(CODIGO_MODELO, "nome")));

        service.construir(ticker, dataRef);

        ArgumentCaptor<List<VerticeConstruido>> dadoCaptor = ArgumentCaptor.forClass(List.class);
        verify(dadoCurvaRepository).substituirVertices(eq(ticker), eq(dataRef), dadoCaptor.capture());
        List<VerticeConstruido> dados = dadoCaptor.getValue();
        assertThat(dados).hasSize(2);
        assertThat(dados.get(0).dataVertice()).isEqualTo(LocalDate.of(2026, 9, 15));
        assertThat(dados.get(0).valor()).isEqualByComparingTo("13.90");
        assertThat(dados.get(1).dataVertice()).isEqualTo(LocalDate.of(2026, 10, 14));
        assertThat(dados.get(1).valor()).isEqualByComparingTo("13.85");

        ArgumentCaptor<List<VerticeConstruido>> curvaCaptor = ArgumentCaptor.forClass(List.class);
        verify(curvaDataRepository).inserirPontos(eq(ticker), eq(dataRef), curvaCaptor.capture());
        assertThat(curvaCaptor.getValue()).isEqualTo(dados);

        // tCurvaData precisa ser esvaziado ANTES de tDadoCurva ser reescrito — FK_tDadoCurva_tCurvaData.
        var ordem = org.mockito.Mockito.inOrder(curvaDataRepository, dadoCurvaRepository);
        ordem.verify(curvaDataRepository).excluirPontos(ticker, dataRef);
        ordem.verify(dadoCurvaRepository).substituirVertices(eq(ticker), eq(dataRef), any());
        ordem.verify(curvaDataRepository).inserirPontos(eq(ticker), eq(dataRef), any());
    }

    @Test
    void lancaExcecaoQuandoNaoHaVerticesBrutos() {
        when(btrsCurvaPrimrRepository.buscarVertices(ticker, dataRef)).thenReturn(List.of());

        assertThatThrownBy(() -> service.construir(ticker, dataRef))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("tBtrsCurvaPrimr");

        verify(dadoCurvaRepository, never()).substituirVertices(any(), any(), any());
        verify(curvaDataRepository, never()).inserirPontos(any(), any(), any());
    }

    @Test
    void lancaExcecaoQuandoNaoHaConfiguracaoVigente() {
        when(btrsCurvaPrimrRepository.buscarVertices(ticker, dataRef)).thenReturn(List.of(
                new VerticeBtrs(1, 1, new BigDecimal("13.90"))
        ));
        when(confgCurvaRepository.buscarMotorCalcVigente(ticker, dataRef)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.construir(ticker, dataRef))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("tConfgCurva");

        verify(dadoCurvaRepository, never()).substituirVertices(any(), any(), any());
    }

    @Test
    void lancaExcecaoQuandoModeloNaoExiste() {
        when(btrsCurvaPrimrRepository.buscarVertices(ticker, dataRef)).thenReturn(List.of(
                new VerticeBtrs(1, 1, new BigDecimal("13.90"))
        ));
        when(confgCurvaRepository.buscarMotorCalcVigente(ticker, dataRef)).thenReturn(Optional.of("MODELO_INEXISTENTE"));
        when(modeloCurvaRepository.buscarPorCodigo("MODELO_INEXISTENTE")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.construir(ticker, dataRef))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("MODELO_INEXISTENTE");

        verify(dadoCurvaRepository, never()).substituirVertices(any(), any(), any());
    }

    @Test
    void lancaExcecaoQuandoModeloEstaDesabilitado() {
        ModeloCurva modeloDesabilitado = ModeloCurva.builtin(CODIGO_MODELO, "nome");
        modeloDesabilitado.desabilitar();

        when(btrsCurvaPrimrRepository.buscarVertices(ticker, dataRef)).thenReturn(List.of(
                new VerticeBtrs(1, 1, new BigDecimal("13.90"))
        ));
        when(confgCurvaRepository.buscarMotorCalcVigente(ticker, dataRef)).thenReturn(Optional.of(CODIGO_MODELO));
        when(modeloCurvaRepository.buscarPorCodigo(CODIGO_MODELO)).thenReturn(Optional.of(modeloDesabilitado));

        assertThatThrownBy(() -> service.construir(ticker, dataRef))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DESABILITADO");

        verify(dadoCurvaRepository, never()).substituirVertices(any(), any(), any());
    }
}
