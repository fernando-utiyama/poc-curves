package br.com.poc.application.port.in.usecase;

import br.com.poc.domain.cadastro.CurvaAuditoria;
import br.com.poc.domain.cadastro.CurvaMercado;
import br.com.poc.domain.cadastro.CurvaMercadoDetalhada;
import br.com.poc.domain.cadastro.CurvaMercadoInput;
import org.springframework.data.domain.Page;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * A curva é identificada pelo nome (cTickerIndcd, PK de tCurvaMercd). O código (cTickerIdtfdUnic) é opcional
 * no banco e só aparece como filtro da listagem.
 */
public interface CurvaMercadoUseCase {

    Page<CurvaMercado> listar(String nome, String codigo, String unidade, String situacao,
                              String provedor, String dono, int pagina, int tamanho);

    Map<String, List<String>> buscarProvedoresPorNomes(Collection<String> nomesCurva);

    CurvaMercadoDetalhada consultar(String nome);

    CurvaMercadoDetalhada criar(CurvaMercadoInput input);

    CurvaMercadoDetalhada alterar(String nome, CurvaMercadoInput input);

    CurvaMercadoDetalhada inativar(String nome);

    CurvaMercadoDetalhada reativar(String nome);

    CurvaAuditoria consultarAuditoria(String nome);
}
