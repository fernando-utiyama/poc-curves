package br.com.poc.application.port.out;

import br.com.poc.domain.cadastro.BtrsCurvaPrimr;
import br.com.poc.domain.cadastro.CurvaPrimrResumo;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface BtrsCurvaPrimrRepositoryPort {

    int proximoId();

    List<BtrsCurvaPrimr> findByNomeCurvaAndDataBase(String nomeCurva, LocalDate dataBase);

    Optional<BtrsCurvaPrimr> findByIdAndNomeCurvaAndDataBase(Integer id, String nomeCurva, LocalDate dataBase);

    BtrsCurvaPrimr salvar(BtrsCurvaPrimr ponto);

    void excluir(Integer id, String nomeCurva, LocalDate dataBase);

    int excluirPorNomeCurvaEDataBase(String nomeCurva, LocalDate dataBase);

    boolean existsCurvaConstruida(String nomeCurva, LocalDate dataBase);

    /** Uma linha por curva e data-base dentro do período. */
    List<CurvaPrimrResumo> listarAgregado(LocalDate de, LocalDate ate, String codigo, String nome);

    /** Uma linha por curva, com a última data-base gravada no bruto dela. */
    List<CurvaPrimrResumo> listarUltimaData(String codigo, String nome);
}
