package br.com.poc.application.port.out;

import br.com.poc.domain.cadastro.BbergCurvaPrimr;
import br.com.poc.domain.cadastro.CurvaPrimrDataBase;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface BbergCurvaPrimrRepositoryPort {

    int proximoId();

    List<BbergCurvaPrimr> findByNomeCurvaAndDataBase(String nomeCurva, LocalDate dataBase);

    Optional<BbergCurvaPrimr> findByIdAndNomeCurvaAndDataBase(Integer id, String nomeCurva, LocalDate dataBase);

    BbergCurvaPrimr salvar(BbergCurvaPrimr vertice);

    void excluir(Integer id, String nomeCurva, LocalDate dataBase);

    int excluirPorNomeCurvaEDataBase(String nomeCurva, LocalDate dataBase);

    boolean existeVerticeConstruido(String nomeCurva, LocalDate dataBase);

    /** Uma linha por curva e data-base no período. */
    List<CurvaPrimrDataBase> listarDatasBase(LocalDate de, LocalDate ate, String codigo, String nome);

    /** Última data-base de cada curva. */
    List<CurvaPrimrDataBase> listarUltimaDataBase(String codigo, String nome);
}
