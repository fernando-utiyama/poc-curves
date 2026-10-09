package br.com.poc.adapter.in.api.rest.dto;

import br.com.poc.domain.cadastro.CurvaMercadoDetalhada;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record CurvaMercadoDetalhadaResponse(
    CurvaMercadoResponse curva,
    @JsonProperty("provedores") List<CurvaProvedorResponse> provedores,
    ConfiguracaoCurvaResponse configuracaoVigente
) {
    public static CurvaMercadoDetalhadaResponse fromDomain(CurvaMercadoDetalhada d) {
        return new CurvaMercadoDetalhadaResponse(
            CurvaMercadoResponse.fromDomain(d.curva(), List.of()),
            d.provedores().stream().map(CurvaProvedorResponse::fromDomain).toList(),
            d.configuracaoVigente() != null ? ConfiguracaoCurvaResponse.fromDomain(d.configuracaoVigente()) : null
        );
    }
}
