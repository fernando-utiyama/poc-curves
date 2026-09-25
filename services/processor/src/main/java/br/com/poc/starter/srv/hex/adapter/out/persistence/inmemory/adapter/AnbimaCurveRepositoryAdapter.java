package br.com.poc.starter.srv.hex.adapter.out.persistence.inmemory.adapter;

import br.com.poc.starter.srv.hex.adapter.out.persistence.inmemory.entity.AnbimaCurveRawEntity;
import br.com.poc.starter.srv.hex.adapter.out.persistence.inmemory.repository.AnbimaJpaRepository;
import br.com.poc.starter.srv.hex.application.model.AnbimaCurveRaw;
import br.com.poc.starter.srv.hex.application.port.out.AnbimaCurveRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

/**
 * Adapter responsável por implementar a porta de saída
 * AnbimaCurveRepositoryPort utilizando Spring Data JPA.
 */
@Component
@RequiredArgsConstructor
public class AnbimaCurveRepositoryAdapter implements AnbimaCurveRepositoryPort {

    private final AnbimaJpaRepository repository;

    @Override
    public Optional<AnbimaCurveRaw> findByTickerAndRefDate(String ticker, LocalDate refDate) {

        return repository.findByCTickerIndcdAndDBaseReft(ticker, refDate)
            .map(entity -> AnbimaCurveRaw.builder()
                .ticker(entity.getCTickerIndcd())
                .refDate(entity.getDBaseReft())
                .vertices(entity.getVVertcCurva())
                .valor(entity.getVPrecoTx())
                .build());
    }

    @Override
    @Transactional
    public void save(AnbimaCurveRaw curve) {

        Optional<AnbimaCurveRawEntity> existing =
            repository.findByCTickerIndcdAndDBaseReft(
                curve.getTicker(),
                curve.getRefDate()
            );

        AnbimaCurveRawEntity entity;

        if (existing.isPresent()) {
            // UPDATE
            entity = existing.get();
            entity.setVVertcCurva(curve.getVertices());
            entity.setVPrecoTx(curve.getValor());
            entity.setLastUpdated(LocalDateTime.now(ZoneOffset.UTC));

        } else {
            // INSERT
            entity = AnbimaCurveRawEntity.builder()
                .cTickerIndcd(curve.getTicker())
                .dBaseReft(curve.getRefDate())
                .vVertcCurva(curve.getVertices())
                .vPrecoTx(curve.getValor())
                .build();
        }

        repository.save(entity);
    }
}
