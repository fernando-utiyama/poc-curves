package br.com.poc.application.port.in.usecase;

import br.com.poc.domain.cadastro.AnbmaCurvaPrimr;
import br.com.poc.domain.cadastro.AnbmaCurvaPrimrInput;
import br.com.poc.domain.cadastro.CurvaPrimrDataBase;
import br.com.poc.domain.cadastro.VerticesPrimrDaData;

import java.time.LocalDate;
import java.util.List;

/** Dado bruto da ANBIMA. A curva é identificada pelo nome (PK). */
public interface AnbmaCurvaPrimrUseCase {

    List<CurvaPrimrDataBase> listarDatasBase(LocalDate de, LocalDate ate, String codigo, String nome);

    VerticesPrimrDaData<AnbmaCurvaPrimr> consultar(String nomeCurva, LocalDate dataBase);

    AnbmaCurvaPrimr incluir(String nomeCurva, LocalDate dataBase, AnbmaCurvaPrimrInput input);

    AnbmaCurvaPrimr alterar(String nomeCurva, LocalDate dataBase, Integer id, AnbmaCurvaPrimrInput input);

    void excluir(String nomeCurva, LocalDate dataBase, Integer id);

    void excluirData(String nomeCurva, LocalDate dataBase);
}
