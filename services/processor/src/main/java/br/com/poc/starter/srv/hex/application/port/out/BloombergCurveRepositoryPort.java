package br.com.poc.starter.srv.hex.application.port.out;

import br.com.poc.starter.srv.hex.application.model.B3CurveRaw;
import br.com.poc.starter.srv.hex.application.model.BloombergCurveRaw;

import java.time.LocalDate;
import java.util.Optional;

public interface BloombergCurveRepositoryPort {
    Optional<BloombergCurveRaw> findByTickerAndRefDate(String ticker, LocalDate refDate);

    void save(BloombergCurveRaw curve);
}
