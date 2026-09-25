package br.com.poc.application.port.out;

import br.com.poc.domain.model.ContextoConstrucaoCurva;
import br.com.poc.domain.model.VerticeCalculado;
import br.com.poc.domain.model.VerticeInsumo;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface CurvaPersistencePort {

    Optional<ContextoConstrucaoCurva> obterContextoConfiguracao(String ticker, LocalDate dataBase);

    String obterMotorConfigurado(String ticker);

    List<VerticeInsumo> carregarInsumos(String ticker, LocalDate dataBase);

    List<VerticeCalculado> carregarVerticesConsolidados(String ticker, LocalDate dataBase);

    void salvarVerticesConsolidados(String ticker, LocalDate dataBase, List<VerticeCalculado> vertices);
}
