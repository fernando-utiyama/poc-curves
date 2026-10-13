package br.com.poc.application.port.in.usecase;

import br.com.poc.domain.cadastro.CurvaAuditoria;
import br.com.poc.domain.cadastro.CurvaMercado;
import br.com.poc.domain.cadastro.CurvaMercadoDetalhada;
import br.com.poc.domain.cadastro.CurvaMercadoInput;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/** A curva é identificada pelo nome (PK); o código é opcional e só filtra a listagem. */
public interface CurvaMercadoUseCase {

    List<CurvaMercado> listar(String nome, String codigo, String unidade, String situacao, String provedor, String dono);

    Map<String, List<String>> buscarProvedoresPorNomes(Collection<String> nomesCurva);

    CurvaMercadoDetalhada consultar(String nome);

    CurvaMercadoDetalhada criar(CurvaMercadoInput input);

    CurvaMercadoDetalhada alterar(String nome, CurvaMercadoInput input);

    CurvaMercadoDetalhada inativar(String nome);

    CurvaMercadoDetalhada reativar(String nome);

    CurvaAuditoria consultarAuditoria(String nome);
}
