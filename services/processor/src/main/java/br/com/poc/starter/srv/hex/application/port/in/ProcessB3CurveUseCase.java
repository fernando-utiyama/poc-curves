package br.com.poc.starter.srv.hex.application.port.in;

import br.com.poc.starter.srv.hex.application.model.B3CurveRaw;

public interface ProcessB3CurveUseCase {
    void execute(B3CurveRaw curve);
}
