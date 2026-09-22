package br.com.poc.starter.srv.hex.application.model;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.time.LocalDate;

@Value
@Builder
@Jacksonized
public class B3CurveRaw {
    String ticker;
    LocalDate refDate;
    Integer diasCorridos;
    Integer diasUteis;
    Double valor;
}
