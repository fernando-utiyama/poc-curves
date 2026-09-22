package br.com.poc.starter.srv.hex.application.port.in;

import br.com.poc.starter.srv.hex.application.model.AnbimaCurveRaw;

public interface ProcessAnbimaCurveUseCase {
    void execute(AnbimaCurveRaw curve);
}
