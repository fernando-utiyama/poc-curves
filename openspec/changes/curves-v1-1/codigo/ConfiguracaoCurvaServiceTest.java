package br.com.poc.application.service;

import br.com.poc.application.exception.BusinessException;
import br.com.poc.application.exception.CadastroErrorCode;
import br.com.poc.application.port.out.ConfiguracaoCurvaRepositoryPort;
import br.com.poc.application.port.out.CurvaMercdRepositoryPort;
import br.com.poc.application.port.out.CurvaPrvdrRepositoryPort;
import br.com.poc.application.port.out.EventosPort;
import br.com.poc.domain.CompoundingCotacao;
import br.com.poc.domain.DayCounterCotacao;
import br.com.poc.domain.SituacaoCurva;
import br.com.poc.domain.Unidade;
import br.com.poc.domain.cadastro.BaseInterpolacao;
import br.com.poc.domain.cadastro.BusinessDayConvention;
import br.com.poc.domain.cadastro.ConfiguracaoCurva;
import br.com.poc.domain.cadastro.ConfiguracaoCurvaResultado;
import br.com.poc.domain.cadastro.CriarConfiguracaoCurvaInput;
import br.com.poc.domain.cadastro.CurvaMercado;
import br.com.poc.domain.cadastro.DayCounter;
import br.com.poc.domain.cadastro.Extrapolacao;
import br.com.poc.domain.cadastro.Frequency;
import br.com.poc.domain.cadastro.ModoArredondamento;
import br.com.poc.domain.cadastro.ParametrosCalculo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConfiguracaoCurvaServiceTest {

    @Mock
    private CurvaMercdRepositoryPort curvaRepositoryPort;

    @Mock
    private CurvaPrvdrRepositoryPort curvaPrvdrRepositoryPort;

    @Mock
    private ConfiguracaoCurvaRepositoryPort configuracaoRepositoryPort;

    @Mock
    private EventosPort eventosPort;

    private ConfiguracaoCurvaService service;

    private final CurvaMercado curvaPadrao = new CurvaMercado(
        "PRE", "DIxPRE", Unidade.TAXA, DayCounterCotacao.Business252,
        CompoundingCotacao.Compounded, "BRL", "BR", null, null,
        SituacaoCurva.ATIVO, LocalDate.of(2026, 1, 1), null,
        LocalDateTime.of(2026, 1, 1, 10, 0), LocalDateTime.of(2026, 1, 1, 10, 0),
        null, null, null
    );

    private ParametrosCalculo paramsPadrao() {
        return new ParametrosCalculo(
            BaseInterpolacao.Discount,
            DayCounter.Business252,
            Frequency.Annual,
            "Brazil",
            "Settlement",
            BusinessDayConvention.Following,
            Extrapolacao.Disabled,
            Extrapolacao.Disabled,
            "10Y",
            4,
            ModoArredondamento.HALF_UP,
            null, null, null,
            Map.of()
        );
    }

    private ConfiguracaoCurva versao(long id, int versao, LocalDate inicio, LocalDate fim) {
        return new ConfiguracaoCurva(id, "DIxPRE", versao, "TAXA_SWAP_B3", "Linear", paramsPadrao(), inicio, fim);
    }

    @BeforeEach
    void setUp() {
        service = new ConfiguracaoCurvaService(
            curvaRepositoryPort,
            curvaPrvdrRepositoryPort,
            configuracaoRepositoryPort,
            eventosPort
        );
    }

    @Test
    @DisplayName("Criar primeira versão de configuração com início no passado")
    void criarPrimeiraVersaoComSucesso() {
        when(curvaRepositoryPort.findByCodigo("PRE")).thenReturn(Optional.of(curvaPadrao));
        when(curvaPrvdrRepositoryPort.findByNomeCurva("DIxPRE")).thenReturn(List.of());
        when(configuracaoRepositoryPort.findUltimaVersao("DIxPRE")).thenReturn(Optional.empty());
        when(configuracaoRepositoryPort.salvar(any(ConfiguracaoCurva.class))).thenAnswer(inv -> inv.getArgument(0));

        CriarConfiguracaoCurvaInput input = new CriarConfiguracaoCurvaInput(
            "TAXA_SWAP_B3",
            "Linear",
            paramsPadrao(),
            LocalDate.of(2026, 1, 1)
        );

        ConfiguracaoCurvaResultado res = service.criar("PRE", input);

        assertNotNull(res);
        assertNotNull(res.configuracao());
        assertEquals(1, res.configuracao().versao());
        assertEquals("DIxPRE", res.configuracao().nomeCurva());
        assertEquals(paramsPadrao(), res.configuracao().parametros());
        verify(configuracaoRepositoryPort).salvar(any(ConfiguracaoCurva.class));
        verify(eventosPort).publicarCadastroAlterado(any());
    }

    @Test
    @DisplayName("Criar nova versão a partir de amanhã fecha a anterior")
    void criarNovaVersaoFechaAnterior() {
        LocalDate hoje = LocalDate.now();
        ConfiguracaoCurva v1 = versao(1L, 1, hoje.minusDays(30), null);

        when(curvaRepositoryPort.findByCodigo("PRE")).thenReturn(Optional.of(curvaPadrao));
        when(curvaPrvdrRepositoryPort.findByNomeCurva("DIxPRE")).thenReturn(List.of());
        when(configuracaoRepositoryPort.findUltimaVersao("DIxPRE")).thenReturn(Optional.of(v1));
        when(configuracaoRepositoryPort.salvar(any(ConfiguracaoCurva.class))).thenAnswer(inv -> inv.getArgument(0));

        LocalDate amanha = hoje.plusDays(1);
        CriarConfiguracaoCurvaInput input = new CriarConfiguracaoCurvaInput(
            "TAXA_SWAP_B3",
            "Linear",
            paramsPadrao(),
            amanha
        );

        ConfiguracaoCurvaResultado res = service.criar("PRE", input);

        assertEquals(2, res.configuracao().versao());

        // A versão anterior foi fechada com fim = amanhã - 1 dia (hoje)
        ArgumentCaptor<ConfiguracaoCurva> captor = ArgumentCaptor.forClass(ConfiguracaoCurva.class);
        verify(configuracaoRepositoryPort, times(2)).salvar(captor.capture());
        List<ConfiguracaoCurva> salvas = captor.getAllValues();
        assertEquals(hoje, salvas.get(0).fimVigencia());
        assertNull(salvas.get(1).fimVigencia());
    }

    @Test
    @DisplayName("Tentativa de criar versão futura com início no passado gera DADOS_INVALIDOS")
    void criarVersaoNoPassadoGeraErro() {
        ConfiguracaoCurva v1 = versao(1L, 1, LocalDate.now().minusDays(10), null);

        when(curvaRepositoryPort.findByCodigo("PRE")).thenReturn(Optional.of(curvaPadrao));
        when(curvaPrvdrRepositoryPort.findByNomeCurva("DIxPRE")).thenReturn(List.of());
        when(configuracaoRepositoryPort.findUltimaVersao("DIxPRE")).thenReturn(Optional.of(v1));

        CriarConfiguracaoCurvaInput input = new CriarConfiguracaoCurvaInput(
            "TAXA_SWAP_B3",
            "Linear",
            paramsPadrao(),
            LocalDate.now().minusDays(5)
        );

        BusinessException ex = assertThrows(BusinessException.class, () -> service.criar("PRE", input));
        assertEquals(CadastroErrorCode.DADOS_INVALIDOS.getCode(), ex.getErrorCode());
    }

    @Test
    @DisplayName("Desistência de versão futura reabre a anterior")
    void desistirVersaoFuturaReabreAnterior() {
        LocalDate hoje = LocalDate.now();
        ConfiguracaoCurva v1 = versao(1L, 1, hoje.minusDays(10), hoje.plusDays(4));
        ConfiguracaoCurva v2 = versao(2L, 2, hoje.plusDays(5), null);

        when(curvaRepositoryPort.findByCodigo("PRE")).thenReturn(Optional.of(curvaPadrao));
        when(configuracaoRepositoryPort.findUltimaVersao("DIxPRE")).thenReturn(Optional.of(v2));
        when(configuracaoRepositoryPort.findByNomeCurvaEVersao("DIxPRE", 1)).thenReturn(Optional.of(v1));

        ConfiguracaoCurvaResultado res = service.excluir("PRE", 2);

        assertNotNull(res);
        verify(configuracaoRepositoryPort).excluir(2L);

        // A v1 foi reaberta com fimVigencia = null
        ArgumentCaptor<ConfiguracaoCurva> captor = ArgumentCaptor.forClass(ConfiguracaoCurva.class);
        verify(configuracaoRepositoryPort).salvar(captor.capture());
        assertNull(captor.getValue().fimVigencia());
    }
}
