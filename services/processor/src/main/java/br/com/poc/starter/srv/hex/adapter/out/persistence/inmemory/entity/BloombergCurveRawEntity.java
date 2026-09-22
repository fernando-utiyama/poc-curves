package br.com.poc.starter.srv.hex.adapter.out.persistence.inmemory.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.*;

import java.time.LocalDate;

@Entity
@Table(name = "BloombergCurveRaw", schema = "mkt")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BloombergCurveRawEntity extends BaseEntity{
    private String ticker;

    @Column(name = "ref_date")
    private LocalDate refDate;

    @Column(name = "fut_last_trade_date")
    private LocalDate futLastTradeDate;

    @Column(name = "settle_date")
    private LocalDate settleDate;

    @Column(name = "day_to_mty")
    private Integer dayToMty;

    @Column(name = "px_settle")
    private Double pxSettle;

    private LocalDate maturity;

    private Double settle;

    @Column(name = "px_mid")
    private Double pxMid;

    @Column(name = "px_last")
    private Double pxLast;
}
