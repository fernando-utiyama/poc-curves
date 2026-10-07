package br.com.poc.application.service;

import br.com.poc.application.port.out.CurvaMercadoPort;
import br.com.poc.application.port.out.EventosPort;
import br.com.poc.domain.cadastro.CadastroCurva;
import br.com.poc.domain.curva.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ProcessarCargaServiceTest {

    private CurvaMercadoPort curvaMercadoPort;
    private ConstruirCurvaService construirCurvaService;
    private ProcessarCargaService service;

    @BeforeEach
    void setUp() {
        curvaMercadoPort = mock(CurvaMercadoPort.class);
        construirCurvaService = mock(ConstruirCurvaService.class);
        var eventosPort = mock(EventosPort.class);
        service = new ProcessarCargaService(curvaMercadoPort, construirCurvaService, eventosPort);
    }

    /** Cadastro mínimo: a construção é simulada, então os parâmetros não são lidos aqui. */
    private CadastroCurva cadastro(String codigo, String nome) {
        var origem = new CurvaProvedor("B3", "TS", "PRE", 1);
        return new CadastroCurva(
            codigo,
            nome,
            "TAXA",
            "Business252",
            "Compounded",
            "ATIVO",
            LocalDate.of(2020, 1, 1),
            LocalDate.of(2040, 12, 31),
            null,
            List.of(origem),
            List.of(),
            1L,
            "TAXA_SWAP_B3",
            "FlatForward",
            null,
            null,
            null,
            1
        );
    }

    @Test
    void testCargaPelaOrigemPrincipal() {
        var dataBase = LocalDate.of(2026, 9, 14);
        var curvaPre = cadastro("PRE", "DIxPRE");

        when(curvaMercadoPort.listarTodas(dataBase)).thenReturn(List.of(curvaPre));
        when(construirCurvaService.executar(any())).thenReturn(
            new ResultadoConstrucao.Construida("PRE", "DIxPRE", dataBase, null, 278, "hashPre", List.of(), 10L)
        );

        var notificacao = new NotificacaoCarga("carga-001", "B3", "TS", dataBase, Map.of("PRE", 278));

        var resultado = service.processar(notificacao);

        assertThat(resultado.idCarga()).isEqualTo("carga-001");
        assertThat(resultado.resultados()).hasSize(1);
        assertThat(resultado.resultados().getFirst().codigo()).isEqualTo("PRE");

        var captor = ArgumentCaptor.forClass(PedidoConstrucao.class);
        verify(construirCurvaService).executar(captor.capture());
        var pedido = captor.getValue();
        assertThat(pedido.codigo()).isEqualTo("PRE");
        assertThat(pedido.dataBase()).isEqualTo(dataBase);
        assertThat(pedido.fonte()).isNull();
        assertThat(pedido.produto()).isNull();
        assertThat(pedido.acionadoPor()).isEqualTo(DisparoConstrucao.CARGA);
    }

    @Test
    void testDuasCurvasNoMesmoCodigo() {
        var dataBase = LocalDate.of(2026, 9, 14);
        var curva1 = cadastro("PRE", "DIxPRE");
        var curva2 = cadastro("PRE_252", "DIxPRE_252");

        when(curvaMercadoPort.listarTodas(dataBase)).thenReturn(List.of(curva1, curva2));
        when(construirCurvaService.executar(any())).thenAnswer(invocation -> {
            PedidoConstrucao p = invocation.getArgument(0);
            return new ResultadoConstrucao.Construida(p.codigo(), p.codigo(), dataBase, null, 278, "hash", List.of(), 10L);
        });

        var notificacao = new NotificacaoCarga("carga-002", "B3", "TS", dataBase, Map.of("PRE", 278));

        var resultado = service.processar(notificacao);

        assertThat(resultado.resultados()).hasSize(2);
        assertThat(resultado.resultados()).extracting(ResultadoConstrucao::codigo)
            .containsExactly("PRE", "PRE_252");

        var captor = ArgumentCaptor.forClass(PedidoConstrucao.class);
        verify(construirCurvaService, times(2)).executar(captor.capture());
        for (var pedido : captor.getAllValues()) {
            assertThat(pedido.fonte()).isNull();
            assertThat(pedido.produto()).isNull();
            assertThat(pedido.acionadoPor()).isEqualTo(DisparoConstrucao.CARGA);
        }
    }
}
