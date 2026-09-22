package br.com.poc.starter.srv.hex.application.model;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.time.LocalDate;

@Value
@Builder
@Jacksonized
public class AnbimaCurveRaw {
    String ticker;
    LocalDate refDate;
    Double vertices;
    Double valor;
}
