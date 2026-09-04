package com.poccurves.engine.application;
import com.poccurves.engine.domain.curva.Vertice;
import com.poccurves.engine.domain.interpolacao.ConfiguracaoInterpolacao;
import com.poccurves.engine.domain.interpolacao.InterpoladorFlatForward;
import com.poccurves.engine.domain.interpolacao.InterpoladorLinear;
import com.poccurves.engine.domain.interpolacao.InterpoladorLogCubico;
import com.poccurves.engine.domain.interpolacao.InterpoladorLogLinear;
import com.poccurves.engine.domain.interpolacao.InterpoladorMonotonicoConvexo;
import com.poccurves.engine.domain.interpolacao.InterpoladorRegistry;
import com.poccurves.engine.domain.interpolacao.InterpoladorSplineCubicoNatural;
import com.poccurves.engine.domain.interpolacao.PoliticaExtrapolacaoEstrita;
import com.poccurves.engine.domain.interpolacao.PoliticaExtrapolacaoForwardConstante;
import com.poccurves.engine.domain.interpolacao.PoliticaExtrapolacaoForwardLinear;
import com.poccurves.engine.domain.interpolacao.PoliticaExtrapolacaoRegistry;
import com.poccurves.engine.domain.interpolacao.PoliticaExtrapolacaoTaxaConstante;
import com.poccurves.engine.domain.versao.MomentoCurva;
import com.poccurves.engine.domain.versao.OrigemVersao;
import com.poccurves.engine.domain.versao.VersaoCurva;

import com.poccurves.engine.dto.EngineDtos.*;
import com.poccurves.engine.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InterpolacaoServiceTest {

    @Mock
    private DefinicaoCurvaResolutionRepositoryPort definicaoCurvaResolutionRepository;
    @Mock
    private VersaoCurvaRepositoryPort versaoCurvaRepository;
    @Mock
    private VerticeCurvaRepositoryPort verticeCurvaRepository;
    @Mock
    private CacheInterpolacaoPort cacheInterpolacaoPort;

    private InterpolacaoService interpolacaoService;

    private final UUID definicaoId = UUID.randomUUID();
    private final UUID versaoId = UUID.randomUUID();
    private VersaoCurva versaoMock;
    private List<Vertice> verticesMock;
    private LocalDate dataReferencia = LocalDate.of(2026, 8, 21);

    @BeforeEach
    void setUp() {
        InterpoladorRegistry interpoladorRegistry = new InterpoladorRegistry(List.of(
                new InterpoladorLinear(),
                new InterpoladorFlatForward(),
                new InterpoladorLogLinear(),
                new InterpoladorLogCubico(),
                new InterpoladorSplineCubicoNatural(),
                new InterpoladorMonotonicoConvexo()
        ));

        PoliticaExtrapolacaoRegistry politicaExtrapolacaoRegistry = new PoliticaExtrapolacaoRegistry(List.of(
                new PoliticaExtrapolacaoEstrita(),
                new PoliticaExtrapolacaoTaxaConstante(),
                new PoliticaExtrapolacaoForwardConstante(),
                new PoliticaExtrapolacaoForwardLinear()
        ));

        interpolacaoService = new InterpolacaoService(
                definicaoCurvaResolutionRepository,
                versaoCurvaRepository,
                verticeCurvaRepository,
                interpoladorRegistry,
                politicaExtrapolacaoRegistry,
                cacheInterpolacaoPort
        );

        versaoMock = mock(VersaoCurva.class);
        lenient().when(versaoMock.id()).thenReturn(versaoId);
        lenient().when(versaoMock.numeroVersao()).thenReturn(3);

        verticesMock = List.of(
                new Vertice(21, 28, dataReferencia.plusDays(28), new BigDecimal("0.10"), null),
                new Vertice(63, 84, dataReferencia.plusDays(84), new BigDecimal("0.11"), null),
                new Vertice(252, 360, dataReferencia.plusDays(360), new BigDecimal("0.13"), null)
        );
    }

    private void mockSucessoBasico(String politicaExtrapolacao) {
        when(definicaoCurvaResolutionRepository.resolverIdPorCodigo("PRE"))
                .thenReturn(Optional.of(definicaoId));
        when(versaoCurvaRepository.buscarVersaoVigentePublicada(definicaoId, dataReferencia, MomentoCurva.FECHAMENTO))
                .thenReturn(Optional.of(versaoMock));
        when(verticeCurvaRepository.buscarPorVersaoCurva(versaoId))
                .thenReturn(verticesMock);
        when(definicaoCurvaResolutionRepository.resolverConfiguracaoInterpolacao("PRE", dataReferencia))
                .thenReturn(Optional.of(new ConfiguracaoInterpolacao("LINEAR", politicaExtrapolacao)));
    }

    @Test
    void testVerticeExato() {
        mockSucessoBasico("ESTRITA");
        InterpolacaoRequest request = new InterpolacaoRequest(dataReferencia, "FECHAMENTO", null, List.of(21));

        Optional<InterpolacaoResponse> responseOpt = interpolacaoService.interpolar("PRE", request);

        assertTrue(responseOpt.isPresent());
        InterpolacaoResponse response = responseOpt.get();
        assertEquals(1, response.resultados().size());
        ItemInterpolacaoResultado item = response.resultados().get(0);

        assertEquals("VERTICE_EXATO", item.status());
        assertEquals(0, new BigDecimal("0.10").compareTo(new BigDecimal(item.taxa())));
        assertNull(item.fatorDesconto());
        assertNull(item.erroMensagem());
    }

    @Test
    void testInterpolado() {
        mockSucessoBasico("ESTRITA");
        InterpolacaoRequest request = new InterpolacaoRequest(dataReferencia, "FECHAMENTO", null, List.of(100));

        Optional<InterpolacaoResponse> responseOpt = interpolacaoService.interpolar("PRE", request);

        assertTrue(responseOpt.isPresent());
        ItemInterpolacaoResultado item = responseOpt.get().resultados().get(0);

        assertEquals("INTERPOLADO", item.status());
        assertNotNull(item.taxa());
    }

    @Test
    void testExtrapoladoComSucesso() {
        mockSucessoBasico("TAXA_CONSTANTE");
        InterpolacaoRequest request = new InterpolacaoRequest(dataReferencia, "FECHAMENTO", null, List.of(500));

        Optional<InterpolacaoResponse> responseOpt = interpolacaoService.interpolar("PRE", request);

        assertTrue(responseOpt.isPresent());
        ItemInterpolacaoResultado item = responseOpt.get().resultados().get(0);

        assertEquals("EXTRAPOLADO", item.status());
        assertNotNull(item.taxa());
    }

    @Test
    void testErroForaIntervaloComPoliticaEstrita() {
        mockSucessoBasico("ESTRITA");
        InterpolacaoRequest request = new InterpolacaoRequest(dataReferencia, "FECHAMENTO", null, List.of(500));

        Optional<InterpolacaoResponse> responseOpt = interpolacaoService.interpolar("PRE", request);

        assertTrue(responseOpt.isPresent());
        ItemInterpolacaoResultado item = responseOpt.get().resultados().get(0);

        assertEquals("ERRO_FORA_INTERVALO", item.status());
        assertNull(item.taxa());
        assertNotNull(item.erroMensagem());
        assertFalse(item.erroMensagem().isBlank());
    }

    @Test
    void testOrdemPreservadaEPrazoInvalidoIsolado() {
        mockSucessoBasico("ESTRITA");
        InterpolacaoRequest request = new InterpolacaoRequest(dataReferencia, "FECHAMENTO", null, List.of(21, 999999, 63));

        Optional<InterpolacaoResponse> responseOpt = interpolacaoService.interpolar("PRE", request);

        assertTrue(responseOpt.isPresent());
        List<ItemInterpolacaoResultado> resultados = responseOpt.get().resultados();
        assertEquals(3, resultados.size());

        assertEquals(21, resultados.get(0).prazoDiasUteis());
        assertEquals("VERTICE_EXATO", resultados.get(0).status());

        assertEquals(999999, resultados.get(1).prazoDiasUteis());
        assertEquals("ERRO_FORA_INTERVALO", resultados.get(1).status());

        assertEquals(63, resultados.get(2).prazoDiasUteis());
        assertEquals("VERTICE_EXATO", resultados.get(2).status());
    }

    @Test
    void testCurvaNaoEncontrada() {
        when(definicaoCurvaResolutionRepository.resolverIdPorCodigo("INVALIDA"))
                .thenReturn(Optional.empty());

        InterpolacaoRequest request = new InterpolacaoRequest(dataReferencia, "FECHAMENTO", null, List.of(21));
        Optional<InterpolacaoResponse> responseOpt = interpolacaoService.interpolar("INVALIDA", request);

        assertTrue(responseOpt.isEmpty());
    }

    @Test
    void testVersaoExplicitaNaoEncontrada() {
        when(definicaoCurvaResolutionRepository.resolverIdPorCodigo("PRE"))
                .thenReturn(Optional.of(definicaoId));
        when(versaoCurvaRepository.buscarPorNumeroVersao(definicaoId, dataReferencia, MomentoCurva.FECHAMENTO, 99))
                .thenReturn(Optional.empty());

        InterpolacaoRequest request = new InterpolacaoRequest(dataReferencia, "FECHAMENTO", 99, List.of(21));
        Optional<InterpolacaoResponse> responseOpt = interpolacaoService.interpolar("PRE", request);

        assertTrue(responseOpt.isEmpty());
    }

    @Test
    void testMomentoDefault() {
        when(definicaoCurvaResolutionRepository.resolverIdPorCodigo("PRE"))
                .thenReturn(Optional.of(definicaoId));
        when(versaoCurvaRepository.buscarVersaoVigentePublicada(any(), any(), eq(MomentoCurva.FECHAMENTO)))
                .thenReturn(Optional.empty());

        InterpolacaoRequest request = new InterpolacaoRequest(dataReferencia, null, null, List.of(21));
        interpolacaoService.interpolar("PRE", request);

        verify(versaoCurvaRepository).buscarVersaoVigentePublicada(definicaoId, dataReferencia, MomentoCurva.FECHAMENTO);
    }

    @Test
    void interpolacaoSobreCurvaImportadaTemMesmoFormatoDeRespostaQueCurvaCalculada() {
        lenient().when(versaoMock.origemVersao()).thenReturn(OrigemVersao.IMPORTADA);
        mockSucessoBasico("ESTRITA");

        InterpolacaoRequest request = new InterpolacaoRequest(dataReferencia, null, null, List.of(63));
        Optional<InterpolacaoResponse> respostaOpt = interpolacaoService.interpolar("PRE", request);

        assertTrue(respostaOpt.isPresent());
        InterpolacaoResponse resposta = respostaOpt.get();
        assertEquals("PRE", resposta.codigoCurva());
        assertEquals(3, resposta.versaoUtilizada());
        assertEquals(1, resposta.resultados().size());
        assertEquals("VERTICE_EXATO", resposta.resultados().get(0).status());
    }

    @Test
    void chaveDeCacheMudaQuandoAVersaoDaCurvaMuda() {
        mockSucessoBasico("ESTRITA");
        InterpolacaoRequest request = new InterpolacaoRequest(dataReferencia, null, null, List.of(63));

        interpolacaoService.interpolar("PRE", request);

        org.mockito.ArgumentCaptor<String> chaveCaptor1 = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(cacheInterpolacaoPort).buscar(chaveCaptor1.capture(), eq(63));
        String chaveVersaoTres = chaveCaptor1.getValue();

        // simula uma NOVA versao publicada: mesmo codigo de curva, mesma data, mas UUID e numero de versao diferentes
        VersaoCurva versaoNovaMock = mock(VersaoCurva.class);
        lenient().when(versaoNovaMock.id()).thenReturn(UUID.randomUUID());
        lenient().when(versaoNovaMock.numeroVersao()).thenReturn(4);
        when(versaoCurvaRepository.buscarVersaoVigentePublicada(definicaoId, dataReferencia, MomentoCurva.FECHAMENTO))
                .thenReturn(Optional.of(versaoNovaMock));
        when(verticeCurvaRepository.buscarPorVersaoCurva(versaoNovaMock.id())).thenReturn(verticesMock);

        org.mockito.Mockito.clearInvocations(cacheInterpolacaoPort);
        interpolacaoService.interpolar("PRE", request);

        org.mockito.ArgumentCaptor<String> chaveCaptor2 = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(cacheInterpolacaoPort).buscar(chaveCaptor2.capture(), eq(63));
        String chaveVersaoQuatro = chaveCaptor2.getValue();

        assertNotEquals(chaveVersaoTres, chaveVersaoQuatro,
                "a chave de cache deve mudar quando a versao muda, tornando entradas antigas inalcancaveis sem invalidacao explicita");
    }
}

