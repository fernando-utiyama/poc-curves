package br.com.poc.application.port.in.usecase;

import br.com.poc.domain.cadastro.BbergCurvaPrimr;
import br.com.poc.domain.cadastro.BbergCurvaPrimrInput;
import br.com.poc.domain.cadastro.CurvaPrimrDataBase;
import br.com.poc.domain.cadastro.VerticesPrimrDaData;

import java.time.LocalDate;
import java.util.List;

/** Dado bruto da Bloomberg. */
public interface BbergCurvaPrimrUseCase {

    List<CurvaPrimrDataBase> listarDatasBase(LocalDate de, LocalDate ate, String codigo, String nome);

    VerticesPrimrDaData<BbergCurvaPrimr> consultar(String nomeCurva, LocalDate dataBase);

    BbergCurvaPrimr incluir(String nomeCurva, LocalDate dataBase, BbergCurvaPrimrInput input);

    BbergCurvaPrimr alterar(String nomeCurva, LocalDate dataBase, Integer id, BbergCurvaPrimrInput input);

    void excluir(String nomeCurva, LocalDate dataBase, Integer id);

    void excluirData(String nomeCurva, LocalDate dataBase);
}
