package br.com.poc.domain.cadastro;

import br.com.poc.domain.CompoundingCotacao;
import br.com.poc.domain.Unidade;
import br.com.poc.domain.aviso.CodigoAvisoCurva;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ValidadorParametrosTest {

    /** Parâmetros válidos da PRE; cada teste troca só o que precisa. */
    private static ParametrosCalculo parametros(BaseInterpolacao base, Frequency frequency, Extrapolacao extrapolacaoFim, Integer casasDecimais) {
        return new ParametrosCalculo(
            base,
            DayCounter.Business252,
            frequency,
            "Brazil",
            "Settlement",
            BusinessDayConvention.Following,
            Extrapolacao.Disabled,
            extrapolacaoFim,
            "10Y",
            casasDecimais,
            ModoArredondamento.HALF_UP,
            null, null, null,
            Map.of()
        );
    }

    private static ParametrosCalculo parametrosValidosPadrao() {
        return parametros(BaseInterpolacao.Discount, Frequency.Annual, Extrapolacao.Disabled, 4);
    }

    @Test
    @DisplayName("Validação com sucesso de parâmetros padrão nativos")
    void validacaoSucessoPadrao() {
        ValidadorParametros.ValidacaoResultado res = ValidadorParametros.validar(
            "TAXA_SWAP_B3",
            "Linear",
            parametrosValidosPadrao(),
            Unidade.TAXA,
            CompoundingCotacao.Compounded,
            List.of(new CurvaProvedor(1L, "PRE", "B3", "TS", "PRE", 1))
        );

        assertTrue(res.isValido(), "Erros encontrados: " + res.erros());
        assertNotNull(res.jsonCompacto());
        assertTrue(res.jsonCompacto().length() <= 1024);
        assertTrue(res.avisos().isEmpty());
    }

    @Test
    @DisplayName("BASE_INTERPOLACAO Price só com PRECO ou PONTOS")
    void baseInterpolacaoPriceComTaxaGeraErro() {
        ParametrosCalculo params = parametros(BaseInterpolacao.Price, null, Extrapolacao.Disabled, 4);

        ValidadorParametros.ValidacaoResultado res = ValidadorParametros.validar(
            "TAXA_SWAP_B3",
            "Linear",
            params,
            Unidade.TAXA,
            CompoundingCotacao.Simple,
            List.of()
        );

        assertFalse(res.isValido());
        assertTrue(res.erros().stream().anyMatch(d -> d.campo().contains("BASE_INTERPOLACAO")));
    }

    @Test
    @DisplayName("FlatForward só é aceito com Linear ou FlatForward")
    void flatForwardInvalidoComCubic() {
        ParametrosCalculo params = parametros(BaseInterpolacao.Discount, Frequency.Annual, Extrapolacao.FlatForward, 4);

        ValidadorParametros.ValidacaoResultado res = ValidadorParametros.validar(
            "TAXA_SWAP_B3",
            "Cubic",
            params,
            Unidade.TAXA,
            CompoundingCotacao.Compounded,
            List.of()
        );

        assertFalse(res.isValido());
        assertTrue(res.erros().stream().anyMatch(d -> d.campo().contains("EXTRAPOLACAO")));
        assertTrue(res.erros().stream().anyMatch(d -> d.motivo().contains("FlatForward só é permitido com interpolador Linear ou FlatForward")));
    }

    @Test
    @DisplayName("FlatForward é aceito com interpolador FlatForward")
    void flatForwardValidoComFlatForward() {
        ParametrosCalculo params = parametros(BaseInterpolacao.Discount, Frequency.Annual, Extrapolacao.FlatForward, 4);

        ValidadorParametros.ValidacaoResultado res = ValidadorParametros.validar(
            "TAXA_SWAP_B3",
            "FlatForward",
            params,
            Unidade.TAXA,
            CompoundingCotacao.Compounded,
            List.of()
        );

        assertTrue(res.isValido(), "Erros encontrados: " + res.erros());
        assertTrue(res.avisos().isEmpty());
    }

    @Test
    @DisplayName("Aviso MODELO_NAO_NATIVO quando modelo ou interpolador for customizado")
    void modeloNaoNativoGeraAviso() {
        ValidadorParametros.ValidacaoResultado res = ValidadorParametros.validar(
            "MODELO_CUSTOM_GROOVY",
            "Linear",
            parametrosValidosPadrao(),
            Unidade.TAXA,
            CompoundingCotacao.Compounded,
            List.of()
        );

        assertTrue(res.isValido());
        assertTrue(res.avisos().stream().anyMatch(a -> a.codigo() == CodigoAvisoCurva.MODELO_NAO_NATIVO));
    }

    @Test
    @DisplayName("Casas decimais fora de faixa e obrigatório ausente geram erro")
    void casasDecimaisInvalidasEObrigatorioAusente() {
        ParametrosCalculo params = parametros(BaseInterpolacao.Discount, null, Extrapolacao.Disabled, 15);

        ValidadorParametros.ValidacaoResultado res = ValidadorParametros.validar(
            "TAXA_SWAP_B3",
            "Linear",
            params,
            Unidade.TAXA,
            CompoundingCotacao.Compounded,
            List.of()
        );

        assertFalse(res.isValido());
        assertEquals(2, res.erros().size(), "Erros encontrados: " + res.erros());
        assertTrue(res.erros().stream().anyMatch(d -> d.campo().equals("parametros.CASAS_DECIMAIS")));
        assertTrue(res.erros().stream().anyMatch(d -> d.campo().equals("parametros.FREQUENCY")));
    }

    @Test
    @DisplayName("Parâmetros ausentes geram erro")
    void parametrosAusentes() {
        ValidadorParametros.ValidacaoResultado res = ValidadorParametros.validar(
            "TAXA_SWAP_B3",
            "Linear",
            null,
            Unidade.TAXA,
            CompoundingCotacao.Compounded,
            List.of()
        );

        assertFalse(res.isValido());
        assertTrue(res.erros().stream().anyMatch(d -> d.campo().equals("parametros")));
    }
}
