package br.com.poc.domain.cadastro;

import java.time.LocalDate;

/** Versão da configuração de cálculo. O JSON de cModDado é gerado só no adaptador de persistência. */
public record ConfiguracaoCurva(
    Long id,
    String nomeCurva,
    Integer versao,
    String modeloConstrucao,
    String interpolador,
    ParametrosCalculo parametros,
    LocalDate inicioVigencia,
    LocalDate fimVigencia
) {

    public ConfiguracaoCurva comFimVigencia(LocalDate fim) {
        return new ConfiguracaoCurva(id, nomeCurva, versao, modeloConstrucao, interpolador, parametros, inicioVigencia, fim);
    }

    public ConfiguracaoCurva comInicioVigencia(LocalDate inicio) {
        return new ConfiguracaoCurva(id, nomeCurva, versao, modeloConstrucao, interpolador, parametros, inicio, fimVigencia);
    }

    public ConfiguracaoCanonicoState toCanonicoState() {
        return new ConfiguracaoCanonicoState(
            fimVigencia,
            inicioVigencia,
            interpolador,
            modeloConstrucao,
            parametros,
            versao
        );
    }
}
