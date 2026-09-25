package br.com.poc.application.port.in;

import br.com.poc.domain.model.MetodoInterpolacao;
import br.com.poc.domain.model.PoliticaExtrapolacao;
import br.com.poc.domain.model.PontoInterpolado;

import java.time.LocalDate;
import java.util.List;

public interface CalcularCurvaUseCase {

    record ResultadoCalculo(
        String ticker,
        LocalDate dataBase,
        int base,
        MetodoInterpolacao metodo,
        PoliticaExtrapolacao politica,
        List<PontoInterpolado> pontos
    ) {}

    ResultadoCalculo executar(
        String ticker,
        LocalDate dataBase,
        Integer base,
        MetodoInterpolacao metodo,
        PoliticaExtrapolacao politica,
        List<Integer> prazos
    );
}
