package br.com.poc.application.port.out;

import br.com.poc.domain.cadastro.BbergCurvaPrimr;
import br.com.poc.domain.cadastro.CurvaPrimrDataGravada;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Mesma forma da porta da B3 e da ANBIMA. */
public interface BbergCurvaPrimrRepositoryPort {

    int proximoId();

    List<BbergCurvaPrimr> findByNomeCurvaAndDataBase(String nomeCurva, LocalDate dataBase);

    Optional<BbergCurvaPrimr> findByIdAndNomeCurvaAndDataBase(Integer id, String nomeCurva, LocalDate dataBase);

    BbergCurvaPrimr salvar(BbergCurvaPrimr vertice);

    void excluir(Integer id, String nomeCurva, LocalDate dataBase);

    int excluirPorNomeCurvaEDataBase(String nomeCurva, LocalDate dataBase);

    boolean existeVerticeConstruido(String nomeCurva, LocalDate dataBase);

    /** Uma linha por curva e data-base dentro do período. */
    List<CurvaPrimrDataGravada> listarDatasGravadas(LocalDate de, LocalDate ate, String codigo, String nome);

    /** Uma linha por curva, com a última data-base gravada no bruto dela. */
    List<CurvaPrimrDataGravada> listarUltimaDataGravada(String codigo, String nome);
}
