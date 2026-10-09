package br.com.poc.application.port.in.usecase;

import br.com.poc.adapter.in.api.rest.dto.CurvaProvedorCurvaResponse;
import br.com.poc.domain.cadastro.AtualizarCurvaProvedorInput;
import br.com.poc.domain.cadastro.CriarCurvaProvedorInput;
import br.com.poc.domain.cadastro.CurvaProvedor;

import java.util.List;

/** Provedores (fontes de dado) ligados a uma curva de mercado. A curva é identificada pelo nome (PK). */
public interface CurvaProvedorUseCase {

    List<CurvaProvedor> listarPorCurva(String nomeCurva);

    CurvaProvedor criar(String nomeCurva, CriarCurvaProvedorInput input);

    CurvaProvedor alterar(String nomeCurva, Long idCurvaProvedor, AtualizarCurvaProvedorInput input);

    void excluir(String nomeCurva, Long idCurvaProvedor);

    List<CurvaProvedorCurvaResponse> listarPorOrigem(String provedor, String produto, String tickerProvedor);
}
