package com.poccurves.engine.application;
import com.poccurves.engine.domain.curva.Vertice;
import com.poccurves.engine.domain.validacao.Classificacao;
import com.poccurves.engine.domain.validacao.ContextoValidacao;
import com.poccurves.engine.domain.validacao.ResultadoTeste;
import com.poccurves.engine.domain.validacao.ResultadoValidacao;
import com.poccurves.engine.domain.versao.DefinicaoResolvida;
import com.poccurves.engine.domain.versao.EstadoVersaoCurva;
import com.poccurves.engine.domain.versao.MomentoCurva;
import com.poccurves.engine.domain.versao.OrigemVersao;
import com.poccurves.engine.domain.versao.VersaoCurva;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PublicacaoCurvaServiceTest {

    @Mock private ConstrucaoCurvaService construcaoCurvaService;
    @Mock private DefinicaoCurvaResolutionRepositoryPort definicaoCurvaResolutionRepository;
    @Mock private VerticeCurvaRepositoryPort verticeCurvaRepository;
    @Mock private InsumoDI1RepositoryPort insumoDI1Repository;
    @Mock private VersaoCurvaRepositoryPort versaoCurvaRepository;
    @Mock private BateriaValidacaoService bateriaValidacaoService;
    @Mock private CurvaPublicadaEventPort curvaPublicadaEventPublisher;

    private PublicacaoCurvaService service;

    private final UUID execucaoId = UUID.randomUUID();
    private final UUID runId = UUID.randomUUID();
    private final UUID versaoCurvaId = UUID.randomUUID();
    private final UUID definicaoId = UUID.randomUUID();
    private final LocalDate dataRef = LocalDate.of(2026, 10, 10);
    private final List<Vertice> verticesPlaceholder = List.of(
            new Vertice(21, null, null, new java.math.BigDecimal("0.10"), null));

    @BeforeEach
    void setup() {
        // Instância real (não mock) — é um wrapper fino sobre versaoCurvaRepository (já mockado),
        // criado num bean próprio porque @Transactional em método privado/auto-invocado não tem
        // efeito nenhum (gotcha real do proxy do Spring, encontrado e corrigido nesta auditoria).
        PromocaoVersaoCurvaService promocaoVersaoCurvaService = new PromocaoVersaoCurvaService(versaoCurvaRepository);
        service = new PublicacaoCurvaService(
                construcaoCurvaService,
                definicaoCurvaResolutionRepository,
                verticeCurvaRepository,
                insumoDI1Repository,
                versaoCurvaRepository,
                bateriaValidacaoService,
                curvaPublicadaEventPublisher,
                promocaoVersaoCurvaService
        );
    }

    @Test
    void fluxoFelizAprovadoEPublicado() {
        when(construcaoCurvaService.construir("PRE_DI", dataRef, "FECHAMENTO", runId, execucaoId))
                .thenReturn(Optional.of(versaoCurvaId));

        DefinicaoResolvida def = new DefinicaoResolvida(
                definicaoId, "PRE_DI", "BOOTSTRAPPED", UUID.randomUUID(), 1, UUID.randomUUID(), List.of(), List.of(), LocalTime.NOON, List.of(), null
        );
        when(definicaoCurvaResolutionRepository.resolverVigente("PRE_DI", dataRef)).thenReturn(Optional.of(def));

        when(verticeCurvaRepository.buscarPorVersaoCurva(versaoCurvaId)).thenReturn(verticesPlaceholder);
        when(insumoDI1Repository.buscarInsumosDI1(any(), any())).thenReturn(List.of());
        when(versaoCurvaRepository.buscarUltimaVersaoPublicadaAnterior(eq(definicaoId), eq(dataRef), eq(MomentoCurva.FECHAMENTO))).thenReturn(Optional.empty());

        BateriaValidacaoService.VereditoBateria veredito = new BateriaValidacaoService.VereditoBateria(List.of(), true);
        when(bateriaValidacaoService.executar(eq(versaoCurvaId), eq(List.of()), any(ContextoValidacao.class))).thenReturn(veredito);

        VersaoCurva versaoMock = VersaoCurva.reconstituir(versaoCurvaId, definicaoId, UUID.randomUUID(), dataRef, MomentoCurva.FECHAMENTO, 1, OrigemVersao.CALCULADA, EstadoVersaoCurva.EM_VALIDACAO, execucaoId, null);
        when(versaoCurvaRepository.buscarPorId(versaoCurvaId)).thenReturn(Optional.of(versaoMock));

        when(versaoCurvaRepository.buscarVersaoVigentePublicada(definicaoId, dataRef, MomentoCurva.FECHAMENTO)).thenReturn(Optional.empty());

        service.processarPedidoConstrucao("PRE_DI", dataRef, "FECHAMENTO", runId, execucaoId);

        verify(versaoCurvaRepository).atualizar(versaoMock);
        assertThat(versaoMock.estado()).isEqualTo(EstadoVersaoCurva.PUBLICADA);
        verify(curvaPublicadaEventPublisher).publicar(eq("PRE_DI"), eq(versaoMock), eq(1), eq(List.of()));
    }

    @Test
    void avisoReprovadoNaoBloqueiaEPublicaComOAvisoNoEvento() {
        when(construcaoCurvaService.construir("PRE_DI", dataRef, "FECHAMENTO", runId, execucaoId))
                .thenReturn(Optional.of(versaoCurvaId));

        DefinicaoResolvida def = new DefinicaoResolvida(
                definicaoId, "PRE_DI", "BOOTSTRAPPED", UUID.randomUUID(), 1, UUID.randomUUID(), List.of(), List.of(), LocalTime.NOON, List.of(), null
        );
        when(definicaoCurvaResolutionRepository.resolverVigente("PRE_DI", dataRef)).thenReturn(Optional.of(def));

        when(verticeCurvaRepository.buscarPorVersaoCurva(versaoCurvaId)).thenReturn(verticesPlaceholder);
        when(insumoDI1Repository.buscarInsumosDI1(any(), any())).thenReturn(List.of());
        when(versaoCurvaRepository.buscarUltimaVersaoPublicadaAnterior(eq(definicaoId), eq(dataRef), eq(MomentoCurva.FECHAMENTO))).thenReturn(Optional.empty());

        ResultadoTeste avisoReprovado = new ResultadoTeste(
                "TESTE_SUAVIDADE", Classificacao.AVISO, ResultadoValidacao.REPROVADO,
                new java.math.BigDecimal("9.9"), new java.math.BigDecimal("5.0"), "variação acima do limite de aviso");
        BateriaValidacaoService.VereditoBateria veredito = new BateriaValidacaoService.VereditoBateria(List.of(avisoReprovado), true);
        when(bateriaValidacaoService.executar(eq(versaoCurvaId), eq(List.of()), any(ContextoValidacao.class))).thenReturn(veredito);

        VersaoCurva versaoMock = VersaoCurva.reconstituir(versaoCurvaId, definicaoId, UUID.randomUUID(), dataRef, MomentoCurva.FECHAMENTO, 1, OrigemVersao.CALCULADA, EstadoVersaoCurva.EM_VALIDACAO, execucaoId, null);
        when(versaoCurvaRepository.buscarPorId(versaoCurvaId)).thenReturn(Optional.of(versaoMock));
        when(versaoCurvaRepository.buscarVersaoVigentePublicada(definicaoId, dataRef, MomentoCurva.FECHAMENTO)).thenReturn(Optional.empty());

        service.processarPedidoConstrucao("PRE_DI", dataRef, "FECHAMENTO", runId, execucaoId);

        // aviso reprovado não é bloqueante: a versão é promovida normalmente (não reprovada)...
        assertThat(versaoMock.estado()).isEqualTo(EstadoVersaoCurva.PUBLICADA);
        // ...e o evento publicado carrega o aviso, não um resultado vazio — quem consome
        // curve.published.v1 precisa saber que a curva subiu com uma ressalva registrada.
        verify(curvaPublicadaEventPublisher).publicar(eq("PRE_DI"), eq(versaoMock), eq(1), eq(List.of(avisoReprovado)));
    }

    @Test
    void resolveCurvaImportadaMesmaDataQuandoVinculoEVersaoExistem() {
        when(construcaoCurvaService.construir("PRE_DI", dataRef, "FECHAMENTO", runId, execucaoId))
                .thenReturn(Optional.of(versaoCurvaId));

        DefinicaoResolvida def = new DefinicaoResolvida(
                definicaoId, "PRE_DI", "BOOTSTRAPPED", UUID.randomUUID(), 1, UUID.randomUUID(), List.of(), List.of(), LocalTime.NOON, List.of(), "B3_CURVA_PRE"
        );
        when(definicaoCurvaResolutionRepository.resolverVigente("PRE_DI", dataRef)).thenReturn(Optional.of(def));

        UUID definicaoImportadaId = UUID.randomUUID();
        when(definicaoCurvaResolutionRepository.resolverIdPorCodigo("B3_CURVA_PRE")).thenReturn(Optional.of(definicaoImportadaId));

        UUID versaoImportadaId = UUID.randomUUID();
        VersaoCurva versaoImportada = VersaoCurva.reconstituir(versaoImportadaId, definicaoImportadaId, UUID.randomUUID(), dataRef, MomentoCurva.FECHAMENTO, 1, OrigemVersao.IMPORTADA, EstadoVersaoCurva.PUBLICADA, UUID.randomUUID(), null);
        when(versaoCurvaRepository.buscarVersaoVigentePublicada(definicaoImportadaId, dataRef, MomentoCurva.FECHAMENTO)).thenReturn(Optional.of(versaoImportada));

        List<Vertice> verticesImportados = List.of(new Vertice(21, null, null, new java.math.BigDecimal("0.11"), null));
        when(verticeCurvaRepository.buscarPorVersaoCurva(versaoImportadaId)).thenReturn(verticesImportados);

        when(verticeCurvaRepository.buscarPorVersaoCurva(versaoCurvaId)).thenReturn(verticesPlaceholder);
        when(insumoDI1Repository.buscarInsumosDI1(any(), any())).thenReturn(List.of());
        when(versaoCurvaRepository.buscarUltimaVersaoPublicadaAnterior(eq(definicaoId), eq(dataRef), eq(MomentoCurva.FECHAMENTO))).thenReturn(Optional.empty());
        when(versaoCurvaRepository.buscarVersaoVigentePublicada(definicaoId, dataRef, MomentoCurva.FECHAMENTO)).thenReturn(Optional.empty());

        BateriaValidacaoService.VereditoBateria veredito = new BateriaValidacaoService.VereditoBateria(List.of(), true);
        var contextoCaptor = org.mockito.ArgumentCaptor.forClass(ContextoValidacao.class);
        when(bateriaValidacaoService.executar(eq(versaoCurvaId), eq(List.of()), contextoCaptor.capture())).thenReturn(veredito);

        VersaoCurva versaoMock = VersaoCurva.reconstituir(versaoCurvaId, definicaoId, UUID.randomUUID(), dataRef, MomentoCurva.FECHAMENTO, 1, OrigemVersao.CALCULADA, EstadoVersaoCurva.EM_VALIDACAO, execucaoId, null);
        when(versaoCurvaRepository.buscarPorId(versaoCurvaId)).thenReturn(Optional.of(versaoMock));

        service.processarPedidoConstrucao("PRE_DI", dataRef, "FECHAMENTO", runId, execucaoId);

        ContextoValidacao contexto = contextoCaptor.getValue();
        assertThat(contexto.curvaImportadaMesmaData()).isPresent();
        assertThat(contexto.curvaImportadaMesmaData().get().vertices()).isEqualTo(verticesImportados);
    }

    @Test
    void curvaImportadaMesmaDataFicaVaziaSemVinculo() {
        when(construcaoCurvaService.construir("PRE_DI", dataRef, "FECHAMENTO", runId, execucaoId))
                .thenReturn(Optional.of(versaoCurvaId));

        DefinicaoResolvida def = new DefinicaoResolvida(
                definicaoId, "PRE_DI", "BOOTSTRAPPED", UUID.randomUUID(), 1, UUID.randomUUID(), List.of(), List.of(), LocalTime.NOON, List.of(), null
        );
        when(definicaoCurvaResolutionRepository.resolverVigente("PRE_DI", dataRef)).thenReturn(Optional.of(def));

        when(verticeCurvaRepository.buscarPorVersaoCurva(versaoCurvaId)).thenReturn(verticesPlaceholder);
        when(insumoDI1Repository.buscarInsumosDI1(any(), any())).thenReturn(List.of());
        when(versaoCurvaRepository.buscarUltimaVersaoPublicadaAnterior(eq(definicaoId), eq(dataRef), eq(MomentoCurva.FECHAMENTO))).thenReturn(Optional.empty());
        when(versaoCurvaRepository.buscarVersaoVigentePublicada(definicaoId, dataRef, MomentoCurva.FECHAMENTO)).thenReturn(Optional.empty());

        BateriaValidacaoService.VereditoBateria veredito = new BateriaValidacaoService.VereditoBateria(List.of(), true);
        var contextoCaptor = org.mockito.ArgumentCaptor.forClass(ContextoValidacao.class);
        when(bateriaValidacaoService.executar(eq(versaoCurvaId), eq(List.of()), contextoCaptor.capture())).thenReturn(veredito);

        VersaoCurva versaoMock = VersaoCurva.reconstituir(versaoCurvaId, definicaoId, UUID.randomUUID(), dataRef, MomentoCurva.FECHAMENTO, 1, OrigemVersao.CALCULADA, EstadoVersaoCurva.EM_VALIDACAO, execucaoId, null);
        when(versaoCurvaRepository.buscarPorId(versaoCurvaId)).thenReturn(Optional.of(versaoMock));

        service.processarPedidoConstrucao("PRE_DI", dataRef, "FECHAMENTO", runId, execucaoId);

        assertThat(contextoCaptor.getValue().curvaImportadaMesmaData()).isEmpty();
        verify(definicaoCurvaResolutionRepository, never()).resolverIdPorCodigo(any());
    }

    @Test
    void fluxoReprovado() {
        when(construcaoCurvaService.construir("PRE_DI", dataRef, "FECHAMENTO", runId, execucaoId))
                .thenReturn(Optional.of(versaoCurvaId));

        DefinicaoResolvida def = new DefinicaoResolvida(
                definicaoId, "PRE_DI", "BOOTSTRAPPED", UUID.randomUUID(), 1, UUID.randomUUID(), List.of(), List.of(), LocalTime.NOON, List.of(), null
        );
        when(definicaoCurvaResolutionRepository.resolverVigente("PRE_DI", dataRef)).thenReturn(Optional.of(def));

        when(verticeCurvaRepository.buscarPorVersaoCurva(versaoCurvaId)).thenReturn(verticesPlaceholder);
        when(insumoDI1Repository.buscarInsumosDI1(any(), any())).thenReturn(List.of());
        when(versaoCurvaRepository.buscarUltimaVersaoPublicadaAnterior(eq(definicaoId), eq(dataRef), eq(MomentoCurva.FECHAMENTO))).thenReturn(Optional.empty());

        ResultadoTeste reprovado = new ResultadoTeste("T1", Classificacao.BLOQUEANTE, ResultadoValidacao.REPROVADO, null, null, "falhou");
        BateriaValidacaoService.VereditoBateria veredito = new BateriaValidacaoService.VereditoBateria(List.of(reprovado), false);
        when(bateriaValidacaoService.executar(eq(versaoCurvaId), eq(List.of()), any(ContextoValidacao.class))).thenReturn(veredito);

        VersaoCurva versaoMock = VersaoCurva.reconstituir(versaoCurvaId, definicaoId, UUID.randomUUID(), dataRef, MomentoCurva.FECHAMENTO, 1, OrigemVersao.CALCULADA, EstadoVersaoCurva.EM_VALIDACAO, execucaoId, null);
        when(versaoCurvaRepository.buscarPorId(versaoCurvaId)).thenReturn(Optional.of(versaoMock));

        service.processarPedidoConstrucao("PRE_DI", dataRef, "FECHAMENTO", runId, execucaoId);

        verify(versaoCurvaRepository).atualizar(versaoMock);
        assertThat(versaoMock.estado()).isEqualTo(EstadoVersaoCurva.REPROVADA);
        verify(curvaPublicadaEventPublisher, never()).publicar(any(), any(), any(Integer.class), any());
        // reprovação por bloqueante deve notificar o orchestrator via callback de falha,
        // senão a execução fica presa em CONSTRUINDO para sempre (gap real fechado por D1d)
        verify(curvaPublicadaEventPublisher).notificarFalha(eq(execucaoId), any(String.class));
    }

    @Test
    void excecaoDuranteProcessamentoNotificaFalhaSemPropagarParaFora() {
        when(construcaoCurvaService.construir("PRE_DI", dataRef, "FECHAMENTO", runId, execucaoId))
                .thenReturn(Optional.of(versaoCurvaId));

        when(definicaoCurvaResolutionRepository.resolverVigente("PRE_DI", dataRef))
                .thenThrow(new IllegalStateException("definição não encontrada"));

        service.processarPedidoConstrucao("PRE_DI", dataRef, "FECHAMENTO", runId, execucaoId);

        verify(curvaPublicadaEventPublisher).notificarFalha(eq(execucaoId), any(String.class));
        verify(curvaPublicadaEventPublisher, never()).publicar(any(), any(), any(Integer.class), any());
    }

    @Test
    void retornaCedoSeConstrucaoDevolveVazio() {
        when(construcaoCurvaService.construir("PRE_DI", dataRef, "FECHAMENTO", runId, execucaoId))
                .thenReturn(Optional.empty());

        service.processarPedidoConstrucao("PRE_DI", dataRef, "FECHAMENTO", runId, execucaoId);

        verify(bateriaValidacaoService, never()).executar(any(), any(), any());
        verify(curvaPublicadaEventPublisher, never()).publicar(any(), any(), any(Integer.class), any());
        verify(curvaPublicadaEventPublisher, never()).notificarFalha(any(), any());
    }

    @Test
    void substituiVersaoAntigaAoPromover() {
        when(construcaoCurvaService.construir("PRE_DI", dataRef, "FECHAMENTO", runId, execucaoId))
                .thenReturn(Optional.of(versaoCurvaId));

        DefinicaoResolvida def = new DefinicaoResolvida(
                definicaoId, "PRE_DI", "BOOTSTRAPPED", UUID.randomUUID(), 1, UUID.randomUUID(), List.of(), List.of(), LocalTime.NOON, List.of(), null
        );
        when(definicaoCurvaResolutionRepository.resolverVigente("PRE_DI", dataRef)).thenReturn(Optional.of(def));

        when(verticeCurvaRepository.buscarPorVersaoCurva(versaoCurvaId)).thenReturn(verticesPlaceholder);
        when(insumoDI1Repository.buscarInsumosDI1(any(), any())).thenReturn(List.of());
        when(versaoCurvaRepository.buscarUltimaVersaoPublicadaAnterior(eq(definicaoId), eq(dataRef), eq(MomentoCurva.FECHAMENTO))).thenReturn(Optional.empty());

        BateriaValidacaoService.VereditoBateria veredito = new BateriaValidacaoService.VereditoBateria(List.of(), true);
        when(bateriaValidacaoService.executar(eq(versaoCurvaId), eq(List.of()), any(ContextoValidacao.class))).thenReturn(veredito);

        VersaoCurva versaoNova = VersaoCurva.reconstituir(versaoCurvaId, definicaoId, UUID.randomUUID(), dataRef, MomentoCurva.FECHAMENTO, 2, OrigemVersao.CALCULADA, EstadoVersaoCurva.EM_VALIDACAO, execucaoId, null);
        when(versaoCurvaRepository.buscarPorId(versaoCurvaId)).thenReturn(Optional.of(versaoNova));

        VersaoCurva versaoAntiga = VersaoCurva.reconstituir(UUID.randomUUID(), definicaoId, UUID.randomUUID(), dataRef, MomentoCurva.FECHAMENTO, 1, OrigemVersao.CALCULADA, EstadoVersaoCurva.PUBLICADA, UUID.randomUUID(), null);
        when(versaoCurvaRepository.buscarVersaoVigentePublicada(definicaoId, dataRef, MomentoCurva.FECHAMENTO)).thenReturn(Optional.of(versaoAntiga));

        service.processarPedidoConstrucao("PRE_DI", dataRef, "FECHAMENTO", runId, execucaoId);

        verify(versaoCurvaRepository).atualizar(versaoAntiga);
        assertThat(versaoAntiga.estado()).isEqualTo(EstadoVersaoCurva.SUBSTITUIDA);

        verify(versaoCurvaRepository).atualizar(versaoNova);
        assertThat(versaoNova.estado()).isEqualTo(EstadoVersaoCurva.PUBLICADA);
    }
}
