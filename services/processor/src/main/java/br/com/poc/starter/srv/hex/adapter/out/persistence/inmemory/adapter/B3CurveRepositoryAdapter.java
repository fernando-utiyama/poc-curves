package br.com.poc.starter.srv.hex.adapter.out.persistence.inmemory.adapter;

import br.com.poc.starter.srv.hex.adapter.out.persistence.inmemory.entity.B3CurveRawEntity;
import br.com.poc.starter.srv.hex.adapter.out.persistence.inmemory.repository.B3JpaRepository;
import br.com.poc.starter.srv.hex.application.model.B3CurveRaw;
import br.com.poc.starter.srv.hex.application.port.out.B3CurveRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

/**
 * Adapter responsável por implementar a porta de saída
 * B3CurveRepositoryPort utilizando Spring Data JPA.
 */
@Component
@RequiredArgsConstructor
public class B3CurveRepositoryAdapter implements B3CurveRepositoryPort {

    private final B3JpaRepository repository;

    @Override
    public Optional<B3CurveRaw> findByTickerAndRefDate(String ticker, LocalDate refDate) {

        return repository.findByTickerAndRefDate(ticker, refDate)
            .map(entity -> B3CurveRaw.builder()
                .ticker(entity.getTicker())
                .refDate(entity.getRefDate())
                .diasCorridos(entity.getDiasCorridos())
                .diasUteis(entity.getDiasUteis())
                .valor(entity.getValor())
                .build());
    }

    @Override
    @Transactional
    public void save(B3CurveRaw curve) {

        Optional<B3CurveRawEntity> existing =
            repository.findByTickerAndRefDate(
                curve.getTicker(),
                curve.getRefDate()
            );

        B3CurveRawEntity entity;

        if (existing.isPresent()) {
            // UPDATE
            entity = existing.get();
            entity.setDiasCorridos(curve.getDiasCorridos());
            entity.setDiasUteis(curve.getDiasUteis());
            entity.setValor(curve.getValor());
            entity.setLastUpdated(LocalDateTime.now(ZoneOffset.UTC));

        } else {
            // INSERT
            entity = B3CurveRawEntity.builder()
                .ticker(curve.getTicker())
                .refDate(curve.getRefDate())
                .diasCorridos(curve.getDiasCorridos())
                .diasUteis(curve.getDiasUteis())
                .valor(curve.getValor())
                .build();
        }

        repository.save(entity);
    }
}
