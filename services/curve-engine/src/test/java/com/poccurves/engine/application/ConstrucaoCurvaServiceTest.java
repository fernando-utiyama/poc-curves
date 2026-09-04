package com.poccurves.engine.application;
import com.poccurves.engine.domain.construcao.InsumoDI1;
import com.poccurves.engine.domain.construcao.ModeloCurva;
import com.poccurves.engine.domain.curva.CurvaJuros;
import com.poccurves.engine.domain.versao.DefinicaoResolvida;
import com.poccurves.engine.domain.versao.MomentoCurva;
import com.poccurves.engine.domain.versao.VersaoCurva;
import com.poccurves.engine.domain.versao.VersaoJaExisteException;

import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConstrucaoCurvaServiceTest {

    @Mock private DefinicaoCurvaResolutionRepositoryPort definicaoCurvaResolutionRepository;
    @Mock private ModeloCurvaRepositoryPort modeloCurvaRepository;
    @Mock private VersaoCurvaRepositoryPort versaoCurvaRepository;
    @Mock private VerticeCurvaRepositoryPort verticeCurvaRepository;
    @Mock private ProcedenciaCurvaRepositoryPort procedenciaCurvaRepository;
    @Mock private InsumoDI1RepositoryPort insumoDI1Repository;
    @Mock private ModeloConstrucaoResolver modeloConstrucaoResolver;

    private final JsonPort jsonPort = valor -> new ObjectMapper().writeValueAsString(valor);

    private ConstrucaoCurvaService service;

    private final LocalDate dataRef = LocalDate.of(2023, 1, 1);
    private final UUID execucaoId = UUID.randomUUID();
    private final UUID runId = UUID.randomUUID();

    @BeforeEach
    void setup() {
        service = new ConstrucaoCurvaService(
                definicaoCurvaResolutionRepository,
                modeloCurvaRepository,
                versaoCurvaRepository,
                verticeCurvaRepository,
                procedenciaCurvaRepository,
                insumoDI1Repository,
                modeloConstrucaoResolver,
                jsonPort
        );
    }

    @Test
    void fluxoFeliz() {
        UUID defId = UUID.randomUUID();
        UUID versaoDefId = UUID.randomUUID();
        UUID modId = UUID.randomUUID();

        DefinicaoResolvida def = new DefinicaoResolvida(
                defId, "PRE_DI", "BOOTSTRAPPED", versaoDefId, 1, modId, List.of("BVBG.086"), List.of(), LocalTime.NOON, List.of(), null
        );

        when(definicaoCurvaResolutionRepository.resolverVigente("PRE_DI", dataRef)).thenReturn(Optional.of(def));

        ModeloCurva modeloMock = mock(ModeloCurva.class);
        when(modeloMock.id()).thenReturn(modId);
        when(modeloCurvaRepository.listarAtivos()).thenReturn(List.of(modeloMock));

        List<InsumoDI1> insumos = List.of(new InsumoDI1("DI1F24", BigDecimal.TEN, 252, LocalDate.of(2024, 1, 1)));
        when(insumoDI1Repository.buscarInsumosDI1(List.of("BVBG.086"), dataRef)).thenReturn(insumos);

        CurvaJuros curvaMock = mock(CurvaJuros.class);
        when(curvaMock.vertices()).thenReturn(List.of());
        when(modeloConstrucaoResolver.construir(any(), any())).thenReturn(curvaMock);

        when(versaoCurvaRepository.proximoNumeroVersao(defId, dataRef, MomentoCurva.FECHAMENTO)).thenReturn(1);

        service.construir("PRE_DI", dataRef, "FECHAMENTO", runId, execucaoId);

        verify(versaoCurvaRepository).inserir(any(VersaoCurva.class));
        verify(verticeCurvaRepository).inserirTodos(any(), any());
        verify(procedenciaCurvaRepository).inserir(any());
    }

    @Test
    void falhaSeDefinicaoNaoEncontrada() {
        when(definicaoCurvaResolutionRepository.resolverVigente("PRE_DI", dataRef)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.construir("PRE_DI", dataRef, "FECHAMENTO", runId, execucaoId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("definição não encontrada");
    }

    @Test
    void falhaSeImported() {
        DefinicaoResolvida def = new DefinicaoResolvida(
                UUID.randomUUID(), "PRE_DI", "IMPORTED", UUID.randomUUID(), 1, UUID.randomUUID(), List.of(), List.of(), LocalTime.NOON, List.of(), null
        );
        when(definicaoCurvaResolutionRepository.resolverVigente("PRE_DI", dataRef)).thenReturn(Optional.of(def));

        assertThatThrownBy(() -> service.construir("PRE_DI", dataRef, "FECHAMENTO", runId, execucaoId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("modoOrigem=IMPORTED");
    }

    @Test
    void falhaSeModeloInexistente() {
        UUID modId = UUID.randomUUID();
        DefinicaoResolvida def = new DefinicaoResolvida(
                UUID.randomUUID(), "PRE_DI", "BOOTSTRAPPED", UUID.randomUUID(), 1, modId, List.of(), List.of(), LocalTime.NOON, List.of(), null
        );
        when(definicaoCurvaResolutionRepository.resolverVigente("PRE_DI", dataRef)).thenReturn(Optional.of(def));
        when(modeloCurvaRepository.listarAtivos()).thenReturn(List.of());

        assertThatThrownBy(() -> service.construir("PRE_DI", dataRef, "FECHAMENTO", runId, execucaoId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("modelo inexistente ou desabilitado");
    }

    @Test
    void falhaSeDependenciaAusente() {
        UUID modId = UUID.randomUUID();
        UUID defId = UUID.randomUUID();

        DefinicaoResolvida def = new DefinicaoResolvida(
                defId, "PRE_DI", "BOOTSTRAPPED", UUID.randomUUID(), 1, modId, List.of(), List.of("CDI"), LocalTime.NOON, List.of(), null
        );
        when(definicaoCurvaResolutionRepository.resolverVigente("PRE_DI", dataRef)).thenReturn(Optional.of(def));

        ModeloCurva modeloMock = mock(ModeloCurva.class);
        when(modeloMock.id()).thenReturn(modId);
        when(modeloCurvaRepository.listarAtivos()).thenReturn(List.of(modeloMock));

        UUID depDefId = UUID.randomUUID();
        DefinicaoResolvida depDef = new DefinicaoResolvida(
                depDefId, "CDI", "BOOTSTRAPPED", UUID.randomUUID(), 1, UUID.randomUUID(), List.of(), List.of(), LocalTime.NOON, List.of(), null
        );
        when(definicaoCurvaResolutionRepository.resolverVigente("CDI", dataRef)).thenReturn(Optional.of(depDef));

        when(versaoCurvaRepository.buscarVersaoVigentePublicada(depDefId, dataRef, MomentoCurva.FECHAMENTO)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.construir("PRE_DI", dataRef, "FECHAMENTO", runId, execucaoId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("dependência ausente: CDI");
    }

    @Test
    void ignoradoQuandoConstrucaoConcorrente() {
        UUID defId = UUID.randomUUID();
        UUID versaoDefId = UUID.randomUUID();
        UUID modId = UUID.randomUUID();

        DefinicaoResolvida def = new DefinicaoResolvida(
                defId, "PRE_DI", "BOOTSTRAPPED", versaoDefId, 1, modId, List.of("BVBG.086"), List.of(), LocalTime.NOON, List.of(), null
        );

        when(definicaoCurvaResolutionRepository.resolverVigente("PRE_DI", dataRef)).thenReturn(Optional.of(def));

        ModeloCurva modeloMock = mock(ModeloCurva.class);
        when(modeloMock.id()).thenReturn(modId);
        when(modeloCurvaRepository.listarAtivos()).thenReturn(List.of(modeloMock));

        List<InsumoDI1> insumos = List.of();
        when(insumoDI1Repository.buscarInsumosDI1(List.of("BVBG.086"), dataRef)).thenReturn(insumos);

        CurvaJuros curvaMock = mock(CurvaJuros.class);
        when(modeloConstrucaoResolver.construir(any(), any())).thenReturn(curvaMock);

        when(versaoCurvaRepository.proximoNumeroVersao(defId, dataRef, MomentoCurva.FECHAMENTO)).thenReturn(1);
        doThrow(VersaoJaExisteException.class).when(versaoCurvaRepository).inserir(any(VersaoCurva.class));

        service.construir("PRE_DI", dataRef, "FECHAMENTO", runId, execucaoId);

        verify(verticeCurvaRepository, never()).inserirTodos(any(), any());
        verify(procedenciaCurvaRepository, never()).inserir(any());
    }

    @Test
    void idempotenteQuandoExecutionIdJaProcessado() {
        when(versaoCurvaRepository.buscarPorExecucaoCurvaId(execucaoId)).thenReturn(Optional.of(mock(VersaoCurva.class)));

        Optional<UUID> resultado = service.construir("PRE_DI", dataRef, "FECHAMENTO", runId, execucaoId);

        assertThat(resultado).isEmpty();
        verify(definicaoCurvaResolutionRepository, never()).resolverVigente(any(), any());
    }

    @Test
    void falhaAoInserirVerticesPropagaExcecaoSemInserirProcedencia() {
        UUID defId = UUID.randomUUID();
        UUID versaoDefId = UUID.randomUUID();
        UUID modId = UUID.randomUUID();

        DefinicaoResolvida def = new DefinicaoResolvida(
                defId, "PRE_DI", "BOOTSTRAPPED", versaoDefId, 1, modId, List.of("BVBG.086"), List.of(), LocalTime.NOON, List.of(), null
        );
        when(definicaoCurvaResolutionRepository.resolverVigente("PRE_DI", dataRef)).thenReturn(Optional.of(def));

        ModeloCurva modeloMock = mock(ModeloCurva.class);
        when(modeloMock.id()).thenReturn(modId);
        when(modeloCurvaRepository.listarAtivos()).thenReturn(List.of(modeloMock));

        List<InsumoDI1> insumos = List.of(new InsumoDI1("DI1F24", BigDecimal.TEN, 252, LocalDate.of(2024, 1, 1)));
        when(insumoDI1Repository.buscarInsumosDI1(List.of("BVBG.086"), dataRef)).thenReturn(insumos);

        CurvaJuros curvaMock = mock(CurvaJuros.class);
        when(curvaMock.vertices()).thenReturn(List.of());
        when(modeloConstrucaoResolver.construir(any(), any())).thenReturn(curvaMock);

        when(versaoCurvaRepository.proximoNumeroVersao(defId, dataRef, MomentoCurva.FECHAMENTO)).thenReturn(1);
        doThrow(new RuntimeException("falha simulada ao inserir vertices")).when(verticeCurvaRepository).inserirTodos(any(), any());

        assertThatThrownBy(() -> service.construir("PRE_DI", dataRef, "FECHAMENTO", runId, execucaoId))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("falha simulada ao inserir vertices");

        // a excecao propaga sem ser capturada por construir() -- e assim que o @Transactional do Spring
        // sabe que precisa reverter a transacao inteira (a insercao de versao_curva que ja tinha acontecido
        // acima nesta mesma chamada). Prova adicional: procedencia nunca chega a ser inserida, porque o
        // metodo real so chama procedenciaCurvaRepository.inserir DEPOIS de verticeCurvaRepository.inserirTodos.
        verify(procedenciaCurvaRepository, never()).inserir(any());
    }

    @Test
    void falhaAoInserirProcedenciaPropagaExcecaoAposVerticesJaInseridos() {
        UUID defId = UUID.randomUUID();
        UUID versaoDefId = UUID.randomUUID();
        UUID modId = UUID.randomUUID();

        DefinicaoResolvida def = new DefinicaoResolvida(
                defId, "PRE_DI", "BOOTSTRAPPED", versaoDefId, 1, modId, List.of("BVBG.086"), List.of(), LocalTime.NOON, List.of(), null
        );
        when(definicaoCurvaResolutionRepository.resolverVigente("PRE_DI", dataRef)).thenReturn(Optional.of(def));

        ModeloCurva modeloMock = mock(ModeloCurva.class);
        when(modeloMock.id()).thenReturn(modId);
        when(modeloCurvaRepository.listarAtivos()).thenReturn(List.of(modeloMock));

        List<InsumoDI1> insumos = List.of(new InsumoDI1("DI1F24", BigDecimal.TEN, 252, LocalDate.of(2024, 1, 1)));
        when(insumoDI1Repository.buscarInsumosDI1(List.of("BVBG.086"), dataRef)).thenReturn(insumos);

        CurvaJuros curvaMock = mock(CurvaJuros.class);
        when(curvaMock.vertices()).thenReturn(List.of());
        when(modeloConstrucaoResolver.construir(any(), any())).thenReturn(curvaMock);

        when(versaoCurvaRepository.proximoNumeroVersao(defId, dataRef, MomentoCurva.FECHAMENTO)).thenReturn(1);
        doThrow(new RuntimeException("falha simulada ao inserir procedencia")).when(procedenciaCurvaRepository).inserir(any());

        assertThatThrownBy(() -> service.construir("PRE_DI", dataRef, "FECHAMENTO", runId, execucaoId))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("falha simulada ao inserir procedencia");

        // vertices e versao ja tinham sido inseridos ANTES da falha -- a excecao ainda assim propaga sem
        // ser capturada, entao o @Transactional do metodo real reverte a transacao inteira (versao +
        // vertices + procedencia), nao so a procedencia que faltou.
        verify(verticeCurvaRepository).inserirTodos(any(), any());
        verify(versaoCurvaRepository).inserir(any(VersaoCurva.class));
    }
}

