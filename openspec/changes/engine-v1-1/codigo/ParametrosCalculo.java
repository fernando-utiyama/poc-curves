package br.com.poc.domain.cadastro;

import br.com.poc.domain.interpolacao.BaseInterpolacao;
import br.com.poc.domain.interpolacao.Extrapolacao;
import br.com.poc.domain.quantlib.BusinessDayConvention;
import br.com.poc.domain.quantlib.DayCounter;
import br.com.poc.domain.quantlib.Frequency;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.RoundingMode;
import java.util.Map;

/**
 * Parâmetros de cálculo como estão em tConfgCurva.cModDado, já tipados.
 * Mesmas chaves que a curves grava. Chave desconhecida, tipo errado ou valor fora de um enum
 * é recusado na leitura (adaptador), e vira CADASTRO_INVALIDO.
 */
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
    @JsonProperty("MODO_ARREDONDAMENTO") RoundingMode modoArredondamento,
    @JsonProperty("VERSAO_SCRIPT_CONSTRUCAO") Integer versaoScriptConstrucao,
    @JsonProperty("VERSAO_SCRIPT_INTERPOLACAO") Integer versaoScriptInterpolacao,
    @JsonProperty("VERSAO_SCRIPT_CALENDARIO") Integer versaoScriptCalendario,
    @JsonProperty("MODELOS_POR_ORIGEM") Map<String, String> modelosPorOrigem
) {

    public ParametrosCalculo {
        modelosPorOrigem = modelosPorOrigem == null ? Map.of() : Map.copyOf(modelosPorOrigem);
    }

    /** Extrapolação ausente vale Disabled, como na spec. */
    public Extrapolacao inicioOuPadrao() {
        return extrapolacaoInicio != null ? extrapolacaoInicio : Extrapolacao.Disabled;
    }

    public Extrapolacao fimOuPadrao() {
        return extrapolacaoFim != null ? extrapolacaoFim : Extrapolacao.Disabled;
    }
}
