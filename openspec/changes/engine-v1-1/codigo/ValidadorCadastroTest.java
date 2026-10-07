package br.com.poc.domain.cadastro;

import br.com.poc.application.exception.BusinessException;
import br.com.poc.domain.calendario.Brazil;
import br.com.poc.domain.calendario.Calendario;
import br.com.poc.domain.construcao.ModeloConstrucao;
import br.com.poc.domain.curva.CurvaMercado;
import br.com.poc.domain.curva.CurvaProvedor;
import br.com.poc.domain.interpolacao.BaseInterpolacao;
import br.com.poc.domain.interpolacao.Extrapolacao;
import br.com.poc.domain.quantlib.BusinessDayConvention;
import br.com.poc.domain.quantlib.DayCounter;
import br.com.poc.domain.quantlib.Frequency;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ValidadorCadastroTest {

    private static final String RAW = "{\"BASE_INTERPOLACAO\":\"Discount\"}";

    private final Function<String, Calendario> resolverCalendario = nome -> "Brazil".equals(nome) ? new Brazil() : null;

    private final Function<String, ModeloConstrucao> resolverModelo = nome -> {
        ModeloConstrucao modelo = mock(ModeloConstrucao.class);
        when(modelo.fonte()).thenReturn("B3");
        when(modelo.produto()).thenReturn("TS");
        return modelo;
    };

    /** Parâmetros válidos da PRE; cada teste troca só o que precisa. */
    private static ParametrosCalculo parametros(BaseInterpolacao base, Frequency frequency, Integer casasDecimais) {
        return new ParametrosCalculo(
            base,
            DayCounter.Business252,
            frequency,
            "Brazil",
            "Settlement",
            BusinessDayConvention.Following,
            Extrapolacao.Disabled,
            Extrapolacao.FlatValue,
            "60Y",
            casasDecimais,
            RoundingMode.HALF_UP,
            null, null, null,
            Map.of()
        );
    }

    private static CadastroCurva cadastro(ParametrosCalculo parametros, String erroParametros, int configuracoesVigentes) {
        return new CadastroCurva(
            "PRE",
            "DIxPRE",
            "TAXA",
            "Business252",
            "Compounded",
            "ATIVO",
            LocalDate.of(2026, 1, 1),
            null,
            null,
            List.of(new CurvaProvedor("B3", "TS", "PRE", 1)),
            List.of(),
            1L,
            "TAXA_SWAP_B3",
            "FlatForward",
            RAW,
            parametros,
            erroParametros,
            configuracoesVigentes
        );
    }

    private CurvaMercado montar(CadastroCurva cadastro) {
        return ValidadorCadastro.montar(cadastro, resolverModelo, resolverCalendario);
    }

    @Test
    @DisplayName("Cadastro válido da PRE monta a curva")
    void cadastroValidoMonta() {
        CurvaMercado curva = montar(cadastro(parametros(BaseInterpolacao.Discount, Frequency.Annual, 7), null, 1));

        assertNotNull(curva);
        assertEquals("PRE", curva.codigo());
    }

    @Test
    @DisplayName("FREQUENCY sem período (NoFrequency) é recusada com Compounded")
    void frequencySemPeriodoRecusada() {
        var cad = cadastro(parametros(BaseInterpolacao.Discount, Frequency.NoFrequency, 7), null, 1);

        assertThrows(BusinessException.class, () -> montar(cad));
    }

    @Test
    @DisplayName("FREQUENCY ausente com Compounded é recusada")
    void frequencyAusenteRecusada() {
        var cad = cadastro(parametros(BaseInterpolacao.Discount, null, 7), null, 1);

        assertThrows(BusinessException.class, () -> montar(cad));
    }

    @Test
    @DisplayName("Casas decimais fora de 0..12 são recusadas")
    void casasDecimaisForaDaFaixa() {
        var cad = cadastro(parametros(BaseInterpolacao.Discount, Frequency.Annual, 15), null, 1);

        assertThrows(BusinessException.class, () -> montar(cad));
    }

    @Test
    @DisplayName("Base Price com unidade TAXA é recusada")
    void basePriceComTaxa() {
        var cad = cadastro(parametros(BaseInterpolacao.Price, Frequency.Annual, 7), null, 1);

        assertThrows(BusinessException.class, () -> montar(cad));
    }

    @Test
    @DisplayName("cModDado ilegível vira CADASTRO_INVALIDO")
    void cModDadoIlegivel() {
        var cad = cadastro(null, "Unrecognized field \"PARAMETRO_INVENTADO\"", 1);

        BusinessException ex = assertThrows(BusinessException.class, () -> montar(cad));
        assertTrue(ex.getMessage().contains("PRE"));
    }

    @Test
    @DisplayName("Sem configuração vigente é recusado")
    void semConfiguracaoVigente() {
        var cad = cadastro(null, null, 0);

        assertThrows(BusinessException.class, () -> montar(cad));
    }

    @Test
    @DisplayName("Calendário com mercado divergente é recusado")
    void mercadoDivergente() {
        ParametrosCalculo p = new ParametrosCalculo(
            BaseInterpolacao.Discount, DayCounter.Business252, Frequency.Annual,
            "Brazil", "FederalReserve", BusinessDayConvention.Following,
            Extrapolacao.Disabled, Extrapolacao.FlatValue, "60Y", 7, RoundingMode.HALF_UP,
            null, null, null, Map.of());

        assertThrows(BusinessException.class, () -> montar(cadastro(p, null, 1)));
    }
}
