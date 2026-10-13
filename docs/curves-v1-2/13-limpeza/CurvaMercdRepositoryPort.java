package br.com.poc.application.port.out;

import br.com.poc.domain.cadastro.CurvaMercado;

import java.util.List;
import java.util.Optional;

public interface CurvaMercdRepositoryPort {

    boolean existsByTicker(String cTickerIndcd);

    boolean existsByCodigo(String codigo);

    boolean existsByCodigoDiferente(String codigo, String nome);

    Optional<CurvaMercado> findByNome(String nome);

    List<String> findAllNomes();

    /** Sem paginação; ordenada por código e, no desempate, por nome. */
    List<CurvaMercado> listar(String nome, String codigo, String unidade, String situacao, String provedor, String dono);

    CurvaMercado salvar(CurvaMercado curva);

    void excluir(String nome);
}
