package br.com.poc.starter.srv.hex.application.service;

import br.com.poc.starter.srv.hex.application.model.BloombergCurveRaw;
import br.com.poc.starter.srv.hex.application.port.in.ProcessBloombergCurveUseCase;
import br.com.poc.starter.srv.hex.application.port.out.BloombergCurveRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ProcessBloombergCurveService implements ProcessBloombergCurveUseCase {
    private final BloombergCurveRepositoryPort repository;

    @Override
    public void execute(BloombergCurveRaw curve) {

        repository.findByTickerAndRefDate(
            curve.getTicker(),
            curve.getRefDate()
        ).ifPresentOrElse(
            existing -> repository.save(curve), // update
            () -> repository.save(curve)        // insert
        );
    }

}
