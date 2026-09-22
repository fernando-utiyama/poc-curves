package br.com.poc.starter.srv.hex.application.service;

import br.com.poc.starter.srv.hex.application.model.AnbimaCurveRaw;
import br.com.poc.starter.srv.hex.application.port.in.ProcessAnbimaCurveUseCase;
import br.com.poc.starter.srv.hex.application.port.out.AnbimaCurveRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ProcessAnbimaCurveService implements ProcessAnbimaCurveUseCase {

    private final AnbimaCurveRepositoryPort repository;

    @Override
    public void execute(AnbimaCurveRaw curve) {

        repository.findByTickerAndRefDate(
            curve.getTicker(),
            curve.getRefDate()
        ).ifPresentOrElse(
            existing -> repository.save(curve), // update
            () -> repository.save(curve)        // insert
        );
    }
}
