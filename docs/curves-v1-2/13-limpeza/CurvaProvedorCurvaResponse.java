package br.com.poc.adapter.in.api.rest.dto;

import br.com.poc.domain.cadastro.CurvaProvedor;

/** Curva que recebe um ticker de um provedor/produto, com a prioridade da ligação. */
public record CurvaProvedorCurvaResponse(String nome, Integer prioridade) {

    public static CurvaProvedorCurvaResponse fromDomain(CurvaProvedor c) {
        return new CurvaProvedorCurvaResponse(c.nomeCurva(), c.prioridade());
    }
}
