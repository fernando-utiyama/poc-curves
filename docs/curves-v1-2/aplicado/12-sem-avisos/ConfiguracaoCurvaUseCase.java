package br.com.poc.application.port.in.usecase;

import br.com.poc.domain.cadastro.ConfiguracaoCurva;
import br.com.poc.domain.cadastro.CriarConfiguracaoCurvaInput;

import java.time.LocalDate;
import java.util.List;

/** Versões da configuração de cálculo da curva. A curva é identificada pelo nome (PK). */
public interface ConfiguracaoCurvaUseCase {

    List<ConfiguracaoCurva> listarPorCurva(String nomeCurva);

    ConfiguracaoCurva consultarVigente(String nomeCurva, LocalDate data);

    /** Confere os parâmetros sem gravar nada: sem erro, não devolve nada; com erro, recusa com a mensagem. */
    void validar(String nomeCurva, CriarConfiguracaoCurvaInput input);

    ConfiguracaoCurva criar(String nomeCurva, CriarConfiguracaoCurvaInput input);

    /** Exclui a versão informada; sem versão, a vigente hoje. */
    void excluir(String nomeCurva, Integer versao);
}
