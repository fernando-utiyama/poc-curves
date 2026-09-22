package br.com.poc.starter.srv.hex.application.port.out;

import br.com.poc.starter.srv.hex.application.model.B3CurveRaw;

import java.time.LocalDate;
import java.util.Optional;

public interface B3CurveRepositoryPort {

    Optional<B3CurveRaw> findByTickerAndRefDate(String ticker, LocalDate refDate);

    void save(B3CurveRaw curve);
}
