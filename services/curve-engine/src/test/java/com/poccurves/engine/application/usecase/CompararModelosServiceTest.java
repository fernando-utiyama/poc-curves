package com.poccurves.engine.application.usecase;
import com.poccurves.engine.application.model.CurvaJuros;
import com.poccurves.engine.application.model.DefinicaoResolvida;
import com.poccurves.engine.application.model.InsumoDI1;
import com.poccurves.engine.application.model.ModeloCurva;
import com.poccurves.engine.application.model.Vertice;
import com.poccurves.engine.application.port.DefinicaoCurvaResolutionRepositoryPort;
import com.poccurves.engine.application.port.InsumoDI1RepositoryPort;
import com.poccurves.engine.application.port.ModeloCurvaRepositoryPort;
import com.poccurves.engine.application.service.ModeloConstrucaoResolver;

import com.poccurves.engine.dto.EngineDtos.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CompararModelosServiceTest {

    private final DefinicaoCurvaResolutionRepositoryPort definicaoCurvaResolutionRepository = mock(DefinicaoCurvaResolutionRepositoryPort.class);
    private final InsumoDI1RepositoryPort insumoDI1Repository = mock(InsumoDI1RepositoryPort.class);
    private final ModeloCurvaRepositoryPort modeloCurvaRepository = mock(ModeloCurvaRepositoryPort.class);
    private final ModeloConstrucaoResolver modeloConstrucaoResolver = mock(ModeloConstrucaoResolver.class);
    private final CompararModelosService service = new CompararModelosService(
            definicaoCurvaResolutionRepository, insumoDI1Repository, modeloCurvaRepository, modeloConstrucaoResolver);

    private final LocalDate dataRef = LocalDate.of(2026, 8, 21);

    @Test
    void mesclaVerticesCoincidentesEExclusivosDosDoisModelos() {
        DefinicaoResolvida def = new DefinicaoResolvida(
                UUID.randomUUID(), "PRE_DI1_B3", "BOOTSTRAPPED", UUID.randomUUID(), 1, UUID.randomUUID(),
                List.of("BVBG.086"), List.of(), LocalTime.NOON, List.of(), null);
        when(definicaoCurvaResolutionRepository.resolverVigente("PRE_DI1_B3", dataRef)).thenReturn(Optional.of(def));

        ModeloCurva modeloA = ModeloCurva.builtin("MODELO_A", "A");
        ModeloCurva modeloB = ModeloCurva.builtin("MODELO_B", "B");
        when(modeloCurvaRepository.buscarPorCodigo("MODELO_A")).thenReturn(Optional.of(modeloA));
        when(modeloCurvaRepository.buscarPorCodigo("MODELO_B")).thenReturn(Optional.of(modeloB));

        List<InsumoDI1> insumos = List.of(new InsumoDI1("DI1F26", BigDecimal.TEN, 21, dataRef));
        when(insumoDI1Repository.buscarInsumosDI1(List.of("BVBG.086"), dataRef)).thenReturn(insumos);

        CurvaJuros curvaA = CurvaJuros.de(List.of(
                new Vertice(21, null, null, new BigDecimal("13.50"), null),
                new Vertice(63, null, null, new BigDecimal("13.20"), null)
        ));
        CurvaJuros curvaB = CurvaJuros.de(List.of(
                new Vertice(21, null, null, new BigDecimal("13.60"), null),
                new Vertice(126, null, null, new BigDecimal("12.90"), null)
        ));
        when(modeloConstrucaoResolver.construir(eq(modeloA), eq(insumos))).thenReturn(curvaA);
        when(modeloConstrucaoResolver.construir(eq(modeloB), eq(insumos))).thenReturn(curvaB);

        ComparacaoModelosRequest request = new ComparacaoModelosRequest("PRE_DI1_B3", dataRef, "FECHAMENTO", "MODELO_A", "MODELO_B");
        ComparacaoResponse resposta = service.comparar(request);

        assertThat(resposta.diferencas()).hasSize(3);

        var item21 = resposta.diferencas().stream().filter(i -> i.prazoDiasUteis() == 21).findFirst().orElseThrow();
        assertThat(item21.status()).isEqualTo("COINCIDENTE");
        assertThat(item21.taxaA()).isEqualByComparingTo("13.50");
        assertThat(item21.taxaB()).isEqualByComparingTo("13.60");
        assertThat(item21.diferencaTaxaBps()).isEqualByComparingTo("10.00");

        var item63 = resposta.diferencas().stream().filter(i -> i.prazoDiasUteis() == 63).findFirst().orElseThrow();
        assertThat(item63.status()).isEqualTo("PRESENTE_APENAS_EM_A");
        assertThat(item63.taxaB()).isNull();

        var item126 = resposta.diferencas().stream().filter(i -> i.prazoDiasUteis() == 126).findFirst().orElseThrow();
        assertThat(item126.status()).isEqualTo("PRESENTE_APENAS_EM_B");
        assertThat(item126.taxaA()).isNull();
    }

    @Test
    void falhaNaConstrucaoDeUmModeloPropagaExcecao() {
        DefinicaoResolvida def = new DefinicaoResolvida(
                UUID.randomUUID(), "PRE_DI1_B3", "BOOTSTRAPPED", UUID.randomUUID(), 1, UUID.randomUUID(),
                List.of("BVBG.086"), List.of(), LocalTime.NOON, List.of(), null);
        when(definicaoCurvaResolutionRepository.resolverVigente("PRE_DI1_B3", dataRef)).thenReturn(Optional.of(def));

        ModeloCurva modeloA = ModeloCurva.builtin("MODELO_A", "A");
        ModeloCurva modeloB = ModeloCurva.builtin("MODELO_B", "B");
        when(modeloCurvaRepository.buscarPorCodigo("MODELO_A")).thenReturn(Optional.of(modeloA));
        when(modeloCurvaRepository.buscarPorCodigo("MODELO_B")).thenReturn(Optional.of(modeloB));

        List<InsumoDI1> insumos = List.of(new InsumoDI1("DI1F26", BigDecimal.TEN, 21, dataRef));
        when(insumoDI1Repository.buscarInsumosDI1(List.of("BVBG.086"), dataRef)).thenReturn(insumos);

        CurvaJuros curvaA = CurvaJuros.de(List.of(
                new Vertice(21, null, null, new BigDecimal("13.50"), null),
                new Vertice(63, null, null, new BigDecimal("13.20"), null)
        ));
        when(modeloConstrucaoResolver.construir(eq(modeloA), any())).thenReturn(curvaA);
        when(modeloConstrucaoResolver.construir(eq(modeloB), any()))
                .thenThrow(new RuntimeException("modelo B falhou ao construir"));

        ComparacaoModelosRequest request = new ComparacaoModelosRequest("PRE_DI1_B3", dataRef, "FECHAMENTO", "MODELO_A", "MODELO_B");
        assertThatThrownBy(() -> service.comparar(request))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("modelo B falhou ao construir");
    }
}

