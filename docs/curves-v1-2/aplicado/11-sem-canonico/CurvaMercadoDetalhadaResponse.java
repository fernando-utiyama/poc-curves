package br.com.poc.adapter.in.api.rest.dto;

import br.com.poc.domain.aviso.AvisoCurva;
import br.com.poc.domain.cadastro.CurvaMercadoDetalhada;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record CurvaMercadoDetalhadaResponse(
    CurvaMercadoResponse curva,
    @JsonProperty("provedores") List<CurvaProvedorResponse> provedores,
    ConfiguracaoCurvaResponse configuracaoVigente,
    List<AvisoCurva> avisos
) {
    public static CurvaMercadoDetalhadaResponse fromDomain(CurvaMercadoDetalhada d) {
        CurvaMercadoResponse curvaResp = CurvaMercadoResponse.fromDomain(d.curva(), d.avisos());
        return new CurvaMercadoDetalhadaResponse(
            curvaResp,
            d.provedores().stream().map(CurvaProvedorResponse::fromDomain).toList(),
            d.configuracaoVigente() != null ? ConfiguracaoCurvaResponse.fromDomain(d.configuracaoVigente()) : null,
            d.avisos()
        );
    }
}
