package br.com.poc.starter.srv.hex.application.service;

import br.com.poc.starter.srv.hex.application.model.B3CurveRaw;
import br.com.poc.starter.srv.hex.application.port.in.ProcessB3CurveUseCase;
import br.com.poc.starter.srv.hex.application.port.out.B3CurveRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ProcessB3CurveService implements ProcessB3CurveUseCase {

    private final B3CurveRepositoryPort repository;

    @Override
    public void execute(B3CurveRaw curve) {
        repository.findByTickerAndRefDate(
            curve.getTicker(),
            curve.getRefDate()
        ).ifPresentOrElse(
            existing -> repository.save(curve), // update
            () -> repository.save(curve)        // insert
        );
    }
}
