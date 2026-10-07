package br.com.poc.domain.cadastro;

import br.com.poc.application.exception.BusinessException;
import br.com.poc.application.service.ResolverModelos;
import br.com.poc.domain.curva.CurvaMercado;
import br.com.poc.domain.curva.CurvaProvedor;
import br.com.poc.domain.interpolacao.BaseInterpolacao;
import br.com.poc.domain.interpolacao.Extrapolacao;
import br.com.poc.domain.quantlib.BusinessDayConvention;
import br.com.poc.domain.quantlib.DayCounter;
import br.com.poc.domain.quantlib.Frequency;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ValidadorCadastroTest {

    private static final CurvaProvedor B3_PRE = new CurvaProvedor("B3", "TS", "PRE", 1);

    private ResolverModelos resolverModelos;

    @BeforeEach
    void setup() {
        resolverModelos = new ResolverModelos();
    }

    /** Parâmetros válidos da PRE. */
    private static ParametrosCalculo paramsPRE() {
        return paramsPRE(BaseInterpolacao.Discount, Frequency.Annual, "Settlement", 7, Map.of());
    }

    private static ParametrosCalculo paramsPRE(BaseInterpolacao base, Frequency freq, String mercado, int casas,
                                               Map<String, String> modelosPorOrigem) {
        return new ParametrosCalculo(
            base, DayCounter.Business252, freq,
            "Brazil", mercado, BusinessDayConvention.Following,
            null, Extrapolacao.FlatForward, "10Y", casas, RoundingMode.HALF_UP,
            null, null, null, modelosPorOrigem);
    }

    /** Cadastro da PRE; cada teste troca só o que precisa. */
    private static CadastroCurva cadastro(String normaDia, String situacao, List<CurvaProvedor> origens, String rotina,
                                          ParametrosCalculo parametros, String erroParametros, int vigentes) {
        return new CadastroCurva(
            "PRE", "DIxPRE", "TAXA", normaDia, "Compounded", situacao,
            LocalDate.of(2020, 1, 1), null, LocalDate.of(2026, 9, 14),
            origens, List.of(),
            1L, "TAXA_SWAP_B3", rotina,
            "{}", parametros, erroParametros, vigentes);
    }

    private static CadastroCurva cadastroValidoPRE() {
        return cadastro("Business252", "ATIVO", List.of(B3_PRE), "FlatForward", paramsPRE(), null, 1);
    }

    private static CadastroCurva comParametros(ParametrosCalculo p) {
        return cadastro("Business252", "ATIVO", List.of(B3_PRE), "FlatForward", p, null, 1);
    }

    private CurvaMercado montar(CadastroCurva cadastro) {
        return ValidadorCadastro.montar(cadastro, resolverModelos::resolverModelo, resolverModelos::resolverCalendario);
    }

    private void assertRejeitado(CadastroCurva cadastro) {
        assertThatThrownBy(() -> montar(cadastro)).isInstanceOf(BusinessException.class);
    }

    @Test
    void testCadastroValidoPRE() {
        var curva = montar(cadastroValidoPRE());
        assertThat(curva.codigo()).isEqualTo("PRE");
        assertThat(curva.ativa()).isTrue();
    }

    @Test
    void testAtivoComEspacos() {
        var cad = cadastro("Business252", "ATIVO    ", List.of(B3_PRE), "FlatForward", paramsPRE(), null, 1);
        assertThat(montar(cad).ativa()).isTrue();
    }

    @Test
    void testCurvaInativaNaoLancaCadastroInvalido() {
        var cad = cadastro("Business252", "INATIVO", List.of(B3_PRE), "FlatForward", paramsPRE(), null, 1);
        assertThat(montar(cad).ativa()).isFalse();
    }

    @Test
    void testChaveDesconhecidaRejeitada() {
        // Chave desconhecida é recusada na leitura de cModDado (adaptador), que entrega o motivo em erroParametros.
        var cad = cadastro("Business252", "ATIVO", List.of(B3_PRE), "FlatForward", null,
            "Unrecognized field \"CHAVE_INEXISTENTE\"", 1);
        assertRejeitado(cad);
    }

    @Test
    void testBusiness252MinusculoRejeitado() {
        assertRejeitado(cadastro("business252", "ATIVO", List.of(B3_PRE), "FlatForward", paramsPRE(), null, 1));
    }

    @Test
    void testMercadoCalendarioDivergenteRejeitado() {
        assertRejeitado(comParametros(paramsPRE(BaseInterpolacao.Discount, Frequency.Annual, "FederalReserve", 7, Map.of())));
    }

    @Test
    void testDuasConfiguracoesVigentesRejeitado() {
        assertRejeitado(cadastro("Business252", "ATIVO", List.of(B3_PRE), "FlatForward", paramsPRE(), null, 2));
    }

    @Test
    void testFlatForwardComCubicRejeitado() {
        assertRejeitado(cadastro("Business252", "ATIVO", List.of(B3_PRE), "Cubic", paramsPRE(), null, 1));
    }

    @Test
    void testFrequencySemPeriodoRejeitada() {
        assertRejeitado(comParametros(paramsPRE(BaseInterpolacao.Discount, Frequency.NoFrequency, "Settlement", 7, Map.of())));
    }

    @Test
    void testFrequencyAusenteComCompoundedRejeitada() {
        assertRejeitado(comParametros(paramsPRE(BaseInterpolacao.Discount, null, "Settlement", 7, Map.of())));
    }

    @Test
    void testCasasDecimaisForaDaFaixaRejeitadas() {
        assertRejeitado(comParametros(paramsPRE(BaseInterpolacao.Discount, Frequency.Annual, "Settlement", 15, Map.of())));
    }

    @Test
    void testBasePriceComTaxaRejeitada() {
        assertRejeitado(comParametros(paramsPRE(BaseInterpolacao.Price, Frequency.Annual, "Settlement", 7, Map.of())));
    }

    @Test
    void testOrigemPedidaSecundariaValida() {
        var params = paramsPRE(BaseInterpolacao.Discount, Frequency.Annual, "Settlement", 7,
            Map.of("BLOOMBERG/BLC2", "SOFR_ZERO_BLOOMBERG"));
        var origens = List.of(B3_PRE, new CurvaProvedor("BLOOMBERG", "BLC2", "SOFR", 2));
        var cad = cadastro("Business252", "ATIVO", origens, "FlatForward", params, null, 1);

        var curva = ValidadorCadastro.montar(cad, new CurvaProvedor("BLOOMBERG", "BLC2", null, 0),
            resolverModelos::resolverModelo, resolverModelos::resolverCalendario);

        assertThat(curva.modeloConstrucao()).isEqualTo("SOFR_ZERO_BLOOMBERG");
    }
}
