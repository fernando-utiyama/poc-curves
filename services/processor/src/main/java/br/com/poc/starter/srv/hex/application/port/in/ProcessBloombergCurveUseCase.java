package br.com.poc.starter.srv.hex.application.port.in;

import br.com.poc.starter.srv.hex.application.model.BloombergCurveRaw;

public interface ProcessBloombergCurveUseCase {
    void execute(BloombergCurveRaw curve);
}
