package br.com.poc.application.port.in.usecase;

import br.com.poc.domain.cadastro.BtrsCurvaPrimr;
import br.com.poc.domain.cadastro.BtrsCurvaPrimrInput;
import br.com.poc.domain.cadastro.CurvaPrimrDataBase;
import br.com.poc.domain.cadastro.VerticesPrimrDaData;

import java.time.LocalDate;
import java.util.List;

/** Dado bruto da B3. A curva é identificada pelo nome (PK). */
public interface BtrsCurvaPrimrUseCase {

    List<CurvaPrimrDataBase> listarDatasBase(LocalDate de, LocalDate ate, String codigo, String nome);

    VerticesPrimrDaData<BtrsCurvaPrimr> consultar(String nomeCurva, LocalDate dataBase);

    BtrsCurvaPrimr incluir(String nomeCurva, LocalDate dataBase, BtrsCurvaPrimrInput input);

    BtrsCurvaPrimr alterar(String nomeCurva, LocalDate dataBase, Integer id, BtrsCurvaPrimrInput input);

    void excluir(String nomeCurva, LocalDate dataBase, Integer id);

    void excluirData(String nomeCurva, LocalDate dataBase);
}
