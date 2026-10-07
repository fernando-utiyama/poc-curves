package br.com.poc.domain.cadastro;

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

public class FixturesCadastroTest {

    private static final LocalDate BASE = LocalDate.of(2026, 9, 14);

    private ResolverModelos resolverModelos;

    @BeforeEach
    void setup() {
        resolverModelos = new ResolverModelos();
    }

    /** Parâmetros no Brazil/Settlement, Following e horizonte 10Y; cada curva troca só o que muda. */
    private static ParametrosCalculo brazil(BaseInterpolacao base, Frequency freq, Extrapolacao inicio, Extrapolacao fim,
                                            int casas, RoundingMode arredondamento) {
        return new ParametrosCalculo(
            base, DayCounter.Business252, freq,
            "Brazil", "Settlement", BusinessDayConvention.Following,
            inicio, fim, "10Y", casas, arredondamento,
            null, null, null, Map.of());
    }

    private static CadastroCurva cadastro(String codigo, String nome, String tipoValor, String normaDia, String tipoJuro,
                                          CurvaProvedor origem, long idConfig, String motor, String rotina,
                                          ParametrosCalculo parametros) {
        return new CadastroCurva(
            codigo, nome, tipoValor, normaDia, tipoJuro, "ATIVO",
            BASE, null, BASE,
            List.of(origem), List.of(),
            idConfig, motor, rotina,
            "{}", parametros, null, 1);
    }

    public static CadastroCurva fixture(String codigo) {
        return switch (codigo) {
            case "PRE" -> cadastro("PRE", "DIxPRE", "TAXA", "Business252", "Compounded",
                new CurvaProvedor("B3", "TS", "PRE", 1), 1L, "TAXA_SWAP_B3", "FlatForward",
                brazil(BaseInterpolacao.Discount, Frequency.Annual, Extrapolacao.Disabled, Extrapolacao.FlatForward, 7, RoundingMode.HALF_UP));
            case "DCL" -> cadastro("DCL", "Cupom limpo de dólar", "TAXA", "Actual360", "Simple",
                new CurvaProvedor("B3", "TS", "DCL", 1), 2L, "TAXA_SWAP_B3", "FlatForward",
                brazil(BaseInterpolacao.Discount, null, Extrapolacao.Disabled, Extrapolacao.FlatForward, 7, RoundingMode.HALF_UP));
            case "DPL" -> cadastro("DPL", "Cupom Limpo DI X IPCA", "TAXA", "Business252", "Compounded",
                new CurvaProvedor("B3", "TS", "DPL", 1), 3L, "TAXA_SWAP_B3", "FlatForward",
                brazil(BaseInterpolacao.Discount, Frequency.Annual, Extrapolacao.Disabled, Extrapolacao.FlatForward, 7, RoundingMode.HALF_UP));
            case "INP" -> cadastro("INP", "IBOVESPA", "PONTOS", null, null,
                new CurvaProvedor("B3", "TS", "INP", 1), 4L, "TAXA_SWAP_B3", "FlatForward",
                brazil(BaseInterpolacao.Price, null, Extrapolacao.Disabled, Extrapolacao.FlatValue, 7, RoundingMode.HALF_UP));
            case "PTX" -> cadastro("PTX", "PTAX - USD", "PRECO", null, null,
                new CurvaProvedor("B3", "TS", "PTX", 1), 5L, "TAXA_SWAP_B3", "FlatForward",
                brazil(BaseInterpolacao.Price, null, Extrapolacao.Disabled, Extrapolacao.Disabled, 7, RoundingMode.DOWN));
            case "NTNB" -> cadastro("NTNB", "NTN-B", "TAXA", "Business252", "Compounded",
                new CurvaProvedor("ANBIMA", "MS", "NTN-B", 1), 6L, "NTNB_BOOTSTRAP_ANBIMA", "FlatForward",
                brazil(BaseInterpolacao.Discount, Frequency.Annual, Extrapolacao.FlatValue, Extrapolacao.FlatForward, 8, RoundingMode.HALF_UP));
            case "SOFR" -> cadastro("SOFR", "SOFR", "TAXA", "Actual360", "Simple",
                new CurvaProvedor("BLOOMBERG", "BLC2", "S0490Z", 1), 7L, "SOFR_ZERO_BLOOMBERG", "Linear",
                new ParametrosCalculo(
                    BaseInterpolacao.CompoundFactor, DayCounter.Actual360, null,
                    "UnitedStates", "FederalReserve", BusinessDayConvention.ModifiedFollowing,
                    Extrapolacao.Disabled, Extrapolacao.FlatValue, "10Y", 7, RoundingMode.HALF_UP,
                    null, null, null, Map.of()));
            default -> throw new IllegalArgumentException("Curva desconhecida: " + codigo);
        };
    }

    @Test
    void testTodasAs7CurvasCarregamSemErro() {
        for (String cod : List.of("PRE", "DCL", "DPL", "INP", "PTX", "NTNB", "SOFR")) {
            CadastroCurva cadastro = fixture(cod);
            CurvaMercado curva = ValidadorCadastro.montar(
                cadastro,
                resolverModelos::resolverModelo,
                resolverModelos::resolverCalendario
            );
            assertThat(curva.codigo()).isEqualTo(cod);
        }
    }
}
