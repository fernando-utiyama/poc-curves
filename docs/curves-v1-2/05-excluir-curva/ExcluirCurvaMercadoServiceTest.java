package br.com.poc.application.service;

import br.com.poc.application.exception.BusinessException;
import br.com.poc.application.exception.CadastroErrorCode;
import br.com.poc.application.exception.NotFoundException;
import br.com.poc.application.port.out.ConfiguracaoCurvaRepositoryPort;
import br.com.poc.application.port.out.CurvaMercdRepositoryPort;
import br.com.poc.application.port.out.CurvaPrvdrRepositoryPort;
import br.com.poc.application.port.out.DadosConstruidosPort;
import br.com.poc.application.port.out.EventosPort;
import br.com.poc.domain.CompoundingCotacao;
import br.com.poc.domain.DayCounterCotacao;
import br.com.poc.domain.SituacaoCurva;
import br.com.poc.domain.Unidade;
import br.com.poc.domain.cadastro.BaseInterpolacao;
import br.com.poc.domain.cadastro.BusinessDayConvention;
import br.com.poc.domain.cadastro.ConfiguracaoCurva;
import br.com.poc.domain.cadastro.CurvaMercado;
import br.com.poc.domain.cadastro.CurvaProvedor;
import br.com.poc.domain.cadastro.DayCounter;
import br.com.poc.domain.cadastro.Extrapolacao;
import br.com.poc.domain.cadastro.Frequency;
import br.com.poc.domain.cadastro.ModoArredondamento;
import br.com.poc.domain.cadastro.ParametrosCalculo;
import br.com.poc.domain.cadastro.LinhasPorTabela;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExcluirCurvaMercadoServiceTest {

    @Mock
    private CurvaMercdRepositoryPort curvaRepositoryPort;

    @Mock
    private CurvaPrvdrRepositoryPort curvaPrvdrRepositoryPort;

    @Mock
    private ConfiguracaoCurvaRepositoryPort configuracaoRepositoryPort;

    @Mock
    private DadosConstruidosPort dadosConstruidosPort;

    @Mock
    private EventosPort eventosPort;

    private ExcluirCurvaMercadoService service;

    private final CurvaMercado curvaPadrao = new CurvaMercado(
        "PRE", "DIxPRE", Unidade.TAXA, DayCounterCotacao.Business252,
        CompoundingCotacao.Compounded, "BRL", "BR", null, null,
        SituacaoCurva.ATIVO, LocalDate.of(2026, 1, 1), null,
        LocalDateTime.of(2026, 1, 1, 10, 0), LocalDateTime.of(2026, 1, 1, 10, 0),
        null, null, null
    );

    private final CurvaProvedor provedorB3 = new CurvaProvedor(10L, "DIxPRE", "B3", "TS", "PRE", 1);

    private ConfiguracaoCurva versao(long id, int versao) {
        ParametrosCalculo params = new ParametrosCalculo(
            BaseInterpolacao.Discount, DayCounter.Business252, Frequency.Annual,
            "Brazil", "Settlement", BusinessDayConvention.Following,
            Extrapolacao.Disabled, Extrapolacao.Disabled, "10Y", 4, ModoArredondamento.HALF_UP,
            null, null, null, Map.of());
        return new ConfiguracaoCurva(id, "DIxPRE", versao, "TAXA_SWAP_B3", "Linear", params, LocalDate.of(2026, 1, 1), null);
    }

    @BeforeEach
    void setUp() {
        service = new ExcluirCurvaMercadoService(
            curvaRepositoryPort, curvaPrvdrRepositoryPort, configuracaoRepositoryPort, dadosConstruidosPort, eventosPort);
    }

    @Test
    @DisplayName("Curva nunca construída sai com provedores e configurações")
    void excluiCurvaSemHistorico() {
        when(curvaRepositoryPort.findByNome("PRE")).thenReturn(Optional.of(curvaPadrao));
        when(configuracaoRepositoryPort.findByNomeCurva("DIxPRE")).thenReturn(List.of(versao(1L, 1), versao(2L, 2)));
        when(curvaPrvdrRepositoryPort.findByNomeCurva("DIxPRE")).thenReturn(List.of(provedorB3));

        service.excluir("PRE");

        verify(configuracaoRepositoryPort).excluir(1L);
        verify(configuracaoRepositoryPort).excluir(2L);
        verify(curvaPrvdrRepositoryPort).excluir(10L, "DIxPRE");
        verify(curvaRepositoryPort).excluir("DIxPRE");
        verify(eventosPort).publicarCadastroAlterado(any());
    }

    @Test
    @DisplayName("Curva com construído ou dado bruto gera CURVA_COM_HISTORICO e não apaga nada")
    void recusaCurvaComHistorico() {
        when(curvaRepositoryPort.findByNome("PRE")).thenReturn(Optional.of(curvaPadrao));
        when(dadosConstruidosPort.dependentes("DIxPRE"))
            .thenReturn(List.of(new LinhasPorTabela("tDadoVertcCurva", 1390), new LinhasPorTabela("tBtrsCurvaPrimr", 278)));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.excluir("PRE"));

        assertEquals(CadastroErrorCode.CURVA_COM_HISTORICO.getCode(), ex.getErrorCode());
        verify(curvaRepositoryPort, never()).excluir(any());
        verify(configuracaoRepositoryPort, never()).excluir(any());
        verify(curvaPrvdrRepositoryPort, never()).excluir(any(), any());
        verify(eventosPort, never()).publicarCadastroAlterado(any());
    }

    @Test
    @DisplayName("Curva inexistente gera NAO_ENCONTRADO")
    void curvaInexistente() {
        when(curvaRepositoryPort.findByNome("XXX")).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> service.excluir("XXX"));
        verifyNoInteractions(dadosConstruidosPort, configuracaoRepositoryPort, eventosPort);
    }
}
