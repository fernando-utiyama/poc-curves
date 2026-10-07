package br.com.poc.domain.cadastro;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * Parâmetros de cálculo da curva (coluna tConfgCurva.cModDado), tipados.
 * As chaves do JSON continuam as da spec (BASE_INTERPOLACAO etc.), então o engine lê o mesmo formato.
 * Valor fora da lista de um enum ou tipo errado é recusado pelo próprio Jackson na entrada.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ParametrosCalculo(
    @JsonProperty("BASE_INTERPOLACAO") BaseInterpolacao baseInterpolacao,
    @JsonProperty("DAY_COUNTER_TEMPO") DayCounter dayCounterTempo,
    @JsonProperty("FREQUENCY") Frequency frequency,
    @JsonProperty("CALENDARIO") String calendario,
    @JsonProperty("MERCADO_CALENDARIO") String mercadoCalendario,
    @JsonProperty("BUSINESS_DAY_CONVENTION") BusinessDayConvention businessDayConvention,
    @JsonProperty("EXTRAPOLACAO_INICIO") Extrapolacao extrapolacaoInicio,
    @JsonProperty("EXTRAPOLACAO_FIM") Extrapolacao extrapolacaoFim,
    @JsonProperty("HORIZONTE") String horizonte,
    @JsonProperty("CASAS_DECIMAIS") Integer casasDecimais,
    @JsonProperty("MODO_ARREDONDAMENTO") ModoArredondamento modoArredondamento,
    @JsonProperty("VERSAO_SCRIPT_CONSTRUCAO") Integer versaoScriptConstrucao,
    @JsonProperty("VERSAO_SCRIPT_INTERPOLACAO") Integer versaoScriptInterpolacao,
    @JsonProperty("VERSAO_SCRIPT_CALENDARIO") Integer versaoScriptCalendario,
    @JsonProperty("MODELOS_POR_ORIGEM") @JsonInclude(JsonInclude.Include.NON_EMPTY) Map<String, String> modelosPorOrigem
) {

    public ParametrosCalculo {
        modelosPorOrigem = modelosPorOrigem == null ? Map.of() : Map.copyOf(modelosPorOrigem);
    }

    /** Extrapolação ausente vale Disabled, como na spec. */
    public ParametrosCalculo comPadroes() {
        return new ParametrosCalculo(
            baseInterpolacao, dayCounterTempo, frequency, calendario, mercadoCalendario, businessDayConvention,
            extrapolacaoInicio != null ? extrapolacaoInicio : Extrapolacao.Disabled,
            extrapolacaoFim != null ? extrapolacaoFim : Extrapolacao.Disabled,
            horizonte, casasDecimais, modoArredondamento,
            versaoScriptConstrucao, versaoScriptInterpolacao, versaoScriptCalendario, modelosPorOrigem);
    }
}
