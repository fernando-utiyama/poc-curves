package br.com.poc.application.port.in;

import br.com.poc.domain.model.VerticeCalculado;

import java.time.LocalDate;
import java.util.List;

public interface ConstruirCurvaUseCase {

    record ResultadoConstrucao(
        String ticker,
        LocalDate dataBase,
        String estrategiaAplicada,
        int quantidadeVerticesCalculados,
        long tempoExecucaoMs,
        List<VerticeCalculado> vertices
    ) {}

    ResultadoConstrucao executar(String ticker, LocalDate dataBase, boolean forcarRecalculo);
}
