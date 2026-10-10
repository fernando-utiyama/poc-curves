package br.com.poc.application.port.in.usecase;

import br.com.poc.domain.cadastro.AtualizarCurvaProvedorInput;
import br.com.poc.domain.cadastro.CriarCurvaProvedorInput;
import br.com.poc.domain.cadastro.CurvaPorOrigem;
import br.com.poc.domain.cadastro.CurvaProvedor;

import java.util.List;

/** Provedores da curva de mercado. */
public interface CurvaProvedorUseCase {

    List<CurvaProvedor> listarPorCurva(String nomeCurva);

    CurvaProvedor criar(String nomeCurva, CriarCurvaProvedorInput input);

    CurvaProvedor alterar(String nomeCurva, Long idCurvaProvedor, AtualizarCurvaProvedorInput input);

    void excluir(String nomeCurva, Long idCurvaProvedor);

    List<CurvaPorOrigem> listarPorOrigem(String provedor, String produto, String tickerProvedor);
}
