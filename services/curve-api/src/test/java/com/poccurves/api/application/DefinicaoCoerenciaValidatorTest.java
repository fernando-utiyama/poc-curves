package com.poccurves.api.application;

import com.poccurves.api.adapter.out.json.JacksonJsonAdapter;
import com.poccurves.api.domain.DefinicaoCurva;
import com.poccurves.api.domain.ModoOrigem;
import com.poccurves.api.domain.VersaoDefinicaoCurva;
import com.poccurves.api.dto.ApiDtos.LimiteValidacaoDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class DefinicaoCoerenciaValidatorTest {

    private DefinicaoCurvaRepositoryPort definicaoRepo;
    private VersaoDefinicaoCurvaRepositoryPort versaoRepo;
    private ModeloCurvaRepositoryPort modeloRepo;
    private DefinicaoCoerenciaValidator validator;

    @BeforeEach
    void setUp() {
        definicaoRepo = Mockito.mock(DefinicaoCurvaRepositoryPort.class);
        versaoRepo = Mockito.mock(VersaoDefinicaoCurvaRepositoryPort.class);
        modeloRepo = Mockito.mock(ModeloCurvaRepositoryPort.class);
        validator = new DefinicaoCoerenciaValidator(definicaoRepo, versaoRepo, modeloRepo, new JacksonJsonAdapter(new ObjectMapper()));
    }

    @Test
    void deveAprovarDefinicaoBootstrappedValida() {
        when(modeloRepo.buscarPorCodigo("BUILTIN_PRE_DI1")).thenReturn(
                Optional.of(new ModeloCurvaRepositoryPort.ModeloCurvaRegistro(
                        UUID.randomUUID(), "BUILTIN_PRE_DI1", "Modelo Builtin", "BUILTIN", "ATIVO", null, null
                ))
        );

        assertThatCode(() -> validator.validarCoerencia(
                "PRE",
                ModoOrigem.BOOTSTRAPPED,
                "FLAT_FORWARD",
                "STRICT",
                "TRUNCATE_8",
                null,
                "BUILTIN_PRE_DI1",
                List.of("BVBG_086", "BVBG_028"),
                List.of(),
                List.of(new LimiteValidacaoDTO("ERRO_MAXIMO_REPRECIFICACAO", "BLOQUEANTE", "0.0001"))
        )).doesNotThrowAnyException();
    }

    @Test
    void deveRecusarDefinicaoBootstrappedSemInsumoIndividual() {
        assertThatThrownBy(() -> validator.validarCoerencia(
                "PRE",
                ModoOrigem.BOOTSTRAPPED,
                "FLAT_FORWARD",
                "STRICT",
                "TRUNCATE_8",
                null,
                null,
                List.of(), // Vazio
                List.of(),
                List.of()
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("BOOTSTRAPPED");
    }

    @Test
    void deveRecusarDefinicaoImportadaComModeloDeCalculo() {
        assertThatThrownBy(() -> validator.validarCoerencia(
                "PRE_B3",
                ModoOrigem.IMPORTED,
                "FLAT_FORWARD",
                "STRICT",
                "TRUNCATE_8",
                UUID.randomUUID(), // Modelo informado para IMPORTED
                null,
                List.of("TAXAS_REFERENCIA"),
                List.of(),
                List.of()
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("não pode referenciar modelo");
    }

    @Test
    void deveRecusarDefinicaoComModeloDesabilitado() {
        UUID modeloId = UUID.randomUUID();
        when(modeloRepo.buscarPorId(modeloId)).thenReturn(
                Optional.of(new ModeloCurvaRepositoryPort.ModeloCurvaRegistro(
                        modeloId, "GROOVY_CUSTOM", "Modelo Custom", "GROOVY", "DESABILITADO", "script", "sha256"
                ))
        );

        assertThatThrownBy(() -> validator.validarCoerencia(
                "PRE",
                ModoOrigem.BOOTSTRAPPED,
                "FLAT_FORWARD",
                "STRICT",
                "TRUNCATE_8",
                modeloId,
                null,
                List.of("BVBG_086"),
                List.of(),
                List.of()
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("desabilitado");
    }

    @Test
    void deveRecusarInterpoladorInvalido() {
        assertThatThrownBy(() -> validator.validarCoerencia(
                "PRE",
                ModoOrigem.BOOTSTRAPPED,
                "INTERPOLADOR_INEXISTENTE",
                "STRICT",
                "TRUNCATE_8",
                null,
                null,
                List.of("BVBG_086"),
                List.of(),
                List.of()
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Interpolador inválido");
    }

    @Test
    void deveRecusarTesteHabilitadoSemLimite() {
        assertThatThrownBy(() -> validator.validarCoerencia(
                "PRE",
                ModoOrigem.BOOTSTRAPPED,
                "FLAT_FORWARD",
                "STRICT",
                "TRUNCATE_8",
                null,
                null,
                List.of("BVBG_086"),
                List.of(),
                List.of(new LimiteValidacaoDTO("TAXAS_NAO_NEGATIVAS", "BLOQUEANTE", null))
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("limite numérico não foi informado");
    }

    @Test
    void deveDetectarCicloDeDependenciaDiretoEIndireto() {
        when(definicaoRepo.existeCodigo("CURVA_B")).thenReturn(true);
        when(definicaoRepo.existeCodigo("CURVA_C")).thenReturn(true);

        DefinicaoCurva defB = DefinicaoCurva.criar("CURVA_B", "Curva B", "BRL", ModoOrigem.BOOTSTRAPPED, LocalTime.of(19, 0), "op");
        DefinicaoCurva defC = DefinicaoCurva.criar("CURVA_C", "Curva C", "BRL", ModoOrigem.BOOTSTRAPPED, LocalTime.of(19, 0), "op");

        when(definicaoRepo.listar(any(), any(), any(), any(), any(), any(int.class), any(int.class)))
                .thenReturn(List.of(defB, defC));

        // Curva B depende de Curva C
        VersaoDefinicaoCurva versaoB = VersaoDefinicaoCurva.primeiraVersao(
                defB.id(), "DU_252", "B3", "LINEAR", "STRICT", "TRUNCATE_8", null, null, null, null, null, null, null,
                "[\"CURVA_C\"]", null, java.time.LocalDate.now()
        );
        when(versaoRepo.buscarMaisRecente(defB.id())).thenReturn(Optional.of(versaoB));

        // Curva C depende de CURVA_A (fechando o ciclo quando CURVA_A tenta depender de CURVA_B)
        VersaoDefinicaoCurva versaoC = VersaoDefinicaoCurva.primeiraVersao(
                defC.id(), "DU_252", "B3", "LINEAR", "STRICT", "TRUNCATE_8", null, null, null, null, null, null, null,
                "[\"CURVA_A\"]", null, java.time.LocalDate.now()
        );
        when(versaoRepo.buscarMaisRecente(defC.id())).thenReturn(Optional.of(versaoC));

        assertThatThrownBy(() -> validator.validarCoerencia(
                "CURVA_A",
                ModoOrigem.BOOTSTRAPPED,
                "FLAT_FORWARD",
                "STRICT",
                "TRUNCATE_8",
                null,
                null,
                List.of("BVBG_086"),
                List.of("CURVA_B"), // CURVA_A -> CURVA_B -> CURVA_C -> CURVA_A
                List.of()
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Ciclo de dependência detectado");
    }
}
