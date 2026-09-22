package br.com.poc.starter.srv.hex.adapter.out.persistence.inmemory.adapter;

import br.com.poc.starter.srv.hex.adapter.out.persistence.inmemory.entity.BloombergCurveRawEntity;
import br.com.poc.starter.srv.hex.adapter.out.persistence.inmemory.repository.BloombergJpaRepository;
import br.com.poc.starter.srv.hex.application.model.BloombergCurveRaw;
import br.com.poc.starter.srv.hex.application.port.out.BloombergCurveRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

/**
 * Adapter responsável por implementar a porta de saída
 * BloombergCurveRepositoryPort utilizando Spring Data JPA.
 */
@Component
@RequiredArgsConstructor
public class BloombergCurveRepositoryAdapter implements BloombergCurveRepositoryPort {

    private final BloombergJpaRepository repository;

    @Override
    public Optional<BloombergCurveRaw> findByTickerAndRefDate(String ticker, LocalDate refDate) {

        return repository.findByTickerAndRefDate(ticker, refDate)
            .map(entity -> BloombergCurveRaw.builder()
                .ticker(entity.getTicker())
                .refDate(entity.getRefDate())
                .futLastTradeDate(entity.getFutLastTradeDate())
                .settleDate(entity.getSettleDate())
                .dayToMty(entity.getDayToMty())
                .pxSettle(entity.getPxSettle())
                .maturity(entity.getMaturity())
                .settle(entity.getSettle())
                .pxMid(entity.getPxMid())
                .pxLast(entity.getPxLast())
                .build());
    }

    @Override
    @Transactional
    public void save(BloombergCurveRaw curve) {

        Optional<BloombergCurveRawEntity> existing =
            repository.findByTickerAndRefDate(
                curve.getTicker(),
                curve.getRefDate()
            );

        BloombergCurveRawEntity entity;

        if (existing.isPresent()) {
            // UPDATE
            entity = existing.get();
            entity.setFutLastTradeDate(curve.getFutLastTradeDate());
            entity.setSettleDate(curve.getSettleDate());
            entity.setDayToMty(curve.getDayToMty());
            entity.setPxSettle(curve.getPxSettle());
            entity.setMaturity(curve.getMaturity());
            entity.setSettle(curve.getSettle());
            entity.setPxMid(curve.getPxMid());
            entity.setPxLast(curve.getPxLast());
            entity.setLastUpdated(LocalDateTime.now(ZoneOffset.UTC));

        } else {
            // INSERT
            entity = BloombergCurveRawEntity.builder()
                .ticker(curve.getTicker())
                .refDate(curve.getRefDate())
                .futLastTradeDate(curve.getFutLastTradeDate())
                .settleDate(curve.getSettleDate())
                .dayToMty(curve.getDayToMty())
                .pxSettle(curve.getPxSettle())
                .maturity(curve.getMaturity())
                .settle(curve.getSettle())
                .pxMid(curve.getPxMid())
                .pxLast(curve.getPxLast())
                .build();
        }

        repository.save(entity);
    }
}
