package br.com.poc.application.port.out;

import br.com.poc.domain.cadastro.AnbmaCurvaPrimr;
import br.com.poc.domain.cadastro.CurvaPrimrResumo;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Mesma forma da porta da B3 e da Bloomberg. */
public interface AnbmaCurvaPrimrRepositoryPort {

    int proximoId();

    List<AnbmaCurvaPrimr> findByNomeCurvaAndDataBase(String nomeCurva, LocalDate dataBase);

    Optional<AnbmaCurvaPrimr> findByIdAndNomeCurvaAndDataBase(Integer id, String nomeCurva, LocalDate dataBase);

    AnbmaCurvaPrimr salvar(AnbmaCurvaPrimr ponto);

    void excluir(Integer id, String nomeCurva, LocalDate dataBase);

    int excluirPorNomeCurvaEDataBase(String nomeCurva, LocalDate dataBase);

    boolean existsCurvaConstruida(String nomeCurva, LocalDate dataBase);

    /** Uma linha por curva e data-base dentro do período. */
    List<CurvaPrimrResumo> listarAgregado(LocalDate de, LocalDate ate, String codigo, String nome);

    /** Uma linha por curva, com a última data-base gravada no bruto dela. */
    List<CurvaPrimrResumo> listarUltimaData(String codigo, String nome);
}
