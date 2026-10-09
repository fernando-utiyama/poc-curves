package br.com.poc.application.port.out;

import java.time.LocalDate;

/** O que o engine construiu (tDadoVertcCurva e a interpolada tDadoCurva). A curves só pergunta e só apaga por data. */
public interface DadoVertcCurvaRepositoryPort {

    /** Há vértices construídos da curva em alguma data entre {@code de} e {@code ate} (inclusive)? */
    boolean existeVerticePorNomeCurvaEPeriodo(String nomeCurva, LocalDate de, LocalDate ate);

    /** Apaga a interpolada (tDadoCurva) e os vértices (tDadoVertcCurva) da curva na data. {@code false} se não havia nada. */
    boolean excluirPorNomeCurvaEDataBase(String nomeCurva, LocalDate dataBase);
}
