package br.com.poc.starter.srv.hex.application.port.out;

import br.com.poc.starter.srv.hex.application.model.AnbimaCurveRaw;

import java.time.LocalDate;
import java.util.Optional;

public interface AnbimaCurveRepositoryPort {

    Optional<AnbimaCurveRaw> findByTickerAndRefDate(String ticker, LocalDate refDate);

    void save(AnbimaCurveRaw curve);
}
