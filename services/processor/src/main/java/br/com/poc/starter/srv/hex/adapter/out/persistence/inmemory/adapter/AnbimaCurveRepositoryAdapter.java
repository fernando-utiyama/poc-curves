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

        return repository.findByTickerAndRefDate(ticker, refDate)
            .map(entity -> AnbimaCurveRaw.builder()
                .ticker(entity.getTicker())
                .refDate(entity.getRefDate())
                .vertices(entity.getVertices())
                .valor(entity.getValor())
                .build());
    }

    @Override
    @Transactional
    public void save(AnbimaCurveRaw curve) {

        Optional<AnbimaCurveRawEntity> existing =
            repository.findByTickerAndRefDate(
                curve.getTicker(),
                curve.getRefDate()
            );

        AnbimaCurveRawEntity entity;

        if (existing.isPresent()) {
            // UPDATE
            entity = existing.get();
            entity.setVertices(curve.getVertices());
            entity.setValor(curve.getValor());
            entity.setLastUpdated(LocalDateTime.now(ZoneOffset.UTC));

        } else {
            // INSERT
            entity = AnbimaCurveRawEntity.builder()
                .ticker(curve.getTicker())
                .refDate(curve.getRefDate())
                .vertices(curve.getVertices())
                .valor(curve.getValor())
                .build();
        }

        repository.save(entity);
    }
}
