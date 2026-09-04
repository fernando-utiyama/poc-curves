package com.poccurves.api.application;

import com.poccurves.api.dto.ApiDtos.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

class CurvaConsultaServiceTest {

    private CurvaConsultaRepositoryPort repo;
    private CurvaConsultaService service;

    @BeforeEach
    void setUp() {
        repo = Mockito.mock(CurvaConsultaRepositoryPort.class);
        service = new CurvaConsultaService(repo);
    }

    @Test
    void deveConsultarVersaoCorrentePublicadaComSucesso() {
        UUID versaoId = UUID.randomUUID();
        UUID defId = UUID.randomUUID();
        LocalDate dataRef = LocalDate.of(2026, 8, 21);

        CurvaConsultaRepositoryPort.VersaoCurvaRegistro reg = new CurvaConsultaRepositoryPort.VersaoCurvaRegistro(
                versaoId, defId, "PRE", "Curva Pré DI1", "BOOTSTRAPPED", dataRef, "FECHAMENTO",
                1, "CALCULADA", "PUBLICADA", UUID.randomUUID(), Instant.now()
        );

        when(repo.buscarVersaoPublicada("PRE", dataRef, "FECHAMENTO")).thenReturn(Optional.of(reg));
        when(repo.buscarTodosVertices(versaoId)).thenReturn(List.of(
                new CurvaConsultaRepositoryPort.VerticeRegistro(21, 31, LocalDate.of(2026, 9, 21), new BigDecimal("14.129000000000"), new BigDecimal("0.988000000000")),
                new CurvaConsultaRepositoryPort.VerticeRegistro(42, 62, LocalDate.of(2026, 10, 21), new BigDecimal("14.250000000000"), new BigDecimal("0.976000000000"))
        ));

        CurvaPublicadaResponse response = service.consultarCurvaPublicada("PRE", dataRef, "FECHAMENTO", null, null);

        assertThat(response.codigoCurva()).isEqualTo("PRE");
        assertThat(response.numeroVersao()).isEqualTo(1);
        assertThat(response.isVersaoCorrente()).isTrue();
        assertThat(response.razaoSelecaoVersao()).isEqualTo("VERSAO_CORRENTE");
        assertThat(response.vertices()).hasSize(2);
        assertThat(response.vertices().get(0).taxa()).isEqualTo(new BigDecimal("14.129000000000"));
    }

    @Test
    void deveLancarExcecaoQuandoNaoHouverCurvaPublicadaNaData() {
        LocalDate dataRef = LocalDate.of(2026, 8, 21);
        when(repo.buscarVersaoPublicada("PRE", dataRef, "FECHAMENTO")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.consultarCurvaPublicada("PRE", dataRef, "FECHAMENTO", null, null))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("Nenhuma versão de curva encontrada");
    }

    @Test
    void deveCompararDuasCurvasSinalizandoPrazosExclusivosESpreads() {
        LocalDate dataRef = LocalDate.of(2026, 8, 21);
        UUID idA = UUID.randomUUID();
        UUID idB = UUID.randomUUID();

        CurvaConsultaRepositoryPort.VersaoCurvaRegistro regA = new CurvaConsultaRepositoryPort.VersaoCurvaRegistro(
                idA, UUID.randomUUID(), "PRE", "Curva Pré Construída", "BOOTSTRAPPED", dataRef, "FECHAMENTO",
                1, "CALCULADA", "PUBLICADA", UUID.randomUUID(), Instant.now()
        );
        CurvaConsultaRepositoryPort.VersaoCurvaRegistro regB = new CurvaConsultaRepositoryPort.VersaoCurvaRegistro(
                idB, UUID.randomUUID(), "PRE_B3", "Curva Pré Oficial", "IMPORTED", dataRef, "FECHAMENTO",
                1, "IMPORTADA", "PUBLICADA", UUID.randomUUID(), Instant.now()
        );

        when(repo.buscarVersaoPublicada("PRE", dataRef, "FECHAMENTO")).thenReturn(Optional.of(regA));
        when(repo.buscarVersaoPublicada("PRE_B3", dataRef, "FECHAMENTO")).thenReturn(Optional.of(regB));

        when(repo.buscarTodosVertices(idA)).thenReturn(List.of(
                new CurvaConsultaRepositoryPort.VerticeRegistro(21, 31, LocalDate.of(2026, 9, 21), new BigDecimal("14.100000000000"), new BigDecimal("0.988000000000")),
                new CurvaConsultaRepositoryPort.VerticeRegistro(42, 62, LocalDate.of(2026, 10, 21), new BigDecimal("14.200000000000"), new BigDecimal("0.976000000000")),
                new CurvaConsultaRepositoryPort.VerticeRegistro(63, 93, LocalDate.of(2026, 11, 21), new BigDecimal("14.300000000000"), new BigDecimal("0.965000000000")) // Exclusivo A
        ));

        when(repo.buscarTodosVertices(idB)).thenReturn(List.of(
                new CurvaConsultaRepositoryPort.VerticeRegistro(21, 31, LocalDate.of(2026, 9, 21), new BigDecimal("14.100000000000"), new BigDecimal("0.988000000000")),
                new CurvaConsultaRepositoryPort.VerticeRegistro(42, 62, LocalDate.of(2026, 10, 21), new BigDecimal("14.250000000000"), new BigDecimal("0.975000000000")), // Diferença -5 bps
                new CurvaConsultaRepositoryPort.VerticeRegistro(84, 124, LocalDate.of(2026, 12, 21), new BigDecimal("14.400000000000"), new BigDecimal("0.954000000000")) // Exclusivo B
        ));

        ComparacaoCurvasRequest req = new ComparacaoCurvasRequest(
                dataRef,
                "FECHAMENTO",
                new CurvaIdentificadorDTO("PRE", null),
                new CurvaIdentificadorDTO("PRE_B3", null)
        );

        ComparacaoCurvasResponse resp = service.compararCurvas(req);

        assertThat(resp.diferencas()).hasSize(4);

        // Prazo 21: Coincidente, taxaA = taxaB -> spread 0
        assertThat(resp.diferencas().get(0).prazoDiasUteis()).isEqualTo(21);
        assertThat(resp.diferencas().get(0).status()).isEqualTo("COINCIDENTE");
        assertThat(resp.diferencas().get(0).diferencaTaxaBps()).isEqualTo(new BigDecimal("-0.0000"));

        // Prazo 42: Coincidente, taxaA (14.20) - taxaB (14.25) = -0.05 * 100 = -5.0000 bps
        assertThat(resp.diferencas().get(1).prazoDiasUteis()).isEqualTo(42);
        assertThat(resp.diferencas().get(1).status()).isEqualTo("COINCIDENTE");
        assertThat(resp.diferencas().get(1).diferencaTaxaBps()).isEqualTo(new BigDecimal("-5.0000"));

        // Prazo 63: Presente apenas em A
        assertThat(resp.diferencas().get(2).prazoDiasUteis()).isEqualTo(63);
        assertThat(resp.diferencas().get(2).status()).isEqualTo("PRESENTE_APENAS_EM_A");
        assertThat(resp.diferencas().get(2).taxaB()).isNull();

        // Prazo 84: Presente apenas em B
        assertThat(resp.diferencas().get(3).prazoDiasUteis()).isEqualTo(84);
        assertThat(resp.diferencas().get(3).status()).isEqualTo("PRESENTE_APENAS_EM_B");
        assertThat(resp.diferencas().get(3).taxaA()).isNull();
    }

    @Test
    void deveInterpolarPrazosEIsolarErrosSobPoliticaEstrita() {
        LocalDate dataRef = LocalDate.of(2026, 8, 21);
        UUID id = UUID.randomUUID();

        CurvaConsultaRepositoryPort.VersaoCurvaRegistro reg = new CurvaConsultaRepositoryPort.VersaoCurvaRegistro(
                id, UUID.randomUUID(), "PRE", "Curva Pré DI1", "BOOTSTRAPPED", dataRef, "FECHAMENTO",
                1, "CALCULADA", "PUBLICADA", UUID.randomUUID(), Instant.now()
        );
        when(repo.buscarVersaoPublicada("PRE", dataRef, "FECHAMENTO")).thenReturn(Optional.of(reg));
        when(repo.buscarTodosVertices(id)).thenReturn(List.of(
                new CurvaConsultaRepositoryPort.VerticeRegistro(21, 31, LocalDate.of(2026, 9, 21), new BigDecimal("14.000000000000"), new BigDecimal("0.988000000000")),
                new CurvaConsultaRepositoryPort.VerticeRegistro(252, 360, LocalDate.of(2027, 8, 21), new BigDecimal("15.000000000000"), new BigDecimal("0.869000000000"))
        ));

        InterpolacaoRequest req = new InterpolacaoRequest(
                dataRef,
                "FECHAMENTO",
                null,
                List.of(21, 100, 5000) // Vértice exato, intermediário, fora do intervalo
        );

        InterpolacaoResponse resp = service.interpolarCurva("PRE", req);

        assertThat(resp.resultados()).hasSize(3);

        // Prazo 21: Vértice exato
        assertThat(resp.resultados().get(0).status()).isEqualTo("VERTICE_EXATO");
        assertThat(resp.resultados().get(0).taxa()).isEqualTo(new BigDecimal("14.000000000000"));

        // Prazo 100: Interpolado
        assertThat(resp.resultados().get(1).status()).isEqualTo("INTERPOLADO");
        assertThat(resp.resultados().get(1).taxa()).isNotNull();

        // Prazo 5000: Erro isolado fora do intervalo
        assertThat(resp.resultados().get(2).status()).isEqualTo("ERRO_FORA_INTERVALO");
        assertThat(resp.resultados().get(2).taxa()).isNull();
        assertThat(resp.resultados().get(2).erroMensagem()).contains("STRICT");
    }
}
