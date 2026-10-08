package br.com.poc.adapter.in.api.rest.dto;

import br.com.poc.domain.aviso.AvisoCurva;
import br.com.poc.domain.cadastro.ConstrucaoApagada;

import java.util.List;

public record ApagarConstrucaoResponse(int verticesApagados, int pontosApagados, List<AvisoCurva> avisos) {

    public static ApagarConstrucaoResponse de(ConstrucaoApagada apagada) {
        return new ApagarConstrucaoResponse(apagada.vertices(), apagada.pontos(), List.of());
    }
}
