package br.com.poc.starter.srv.hex.application.model;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.time.LocalDate;

@Value
@Builder
@Jacksonized
public class BloombergCurveRaw {
    String ticker;
    LocalDate refDate;
    LocalDate futLastTradeDate;
    LocalDate settleDate;
    Integer dayToMty;
    Double pxSettle;
    LocalDate maturity;
    Double settle;
    Double pxMid;
    Double pxLast;
}
