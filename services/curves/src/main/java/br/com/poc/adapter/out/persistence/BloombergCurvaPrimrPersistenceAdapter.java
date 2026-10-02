package br.com.poc.adapter.out.persistence;

import br.com.poc.adapter.out.persistence.entity.BloombergCurvaPrimrEntity;
import br.com.poc.adapter.out.persistence.mapper.BloombergCurvaPrimrMapper;
import br.com.poc.adapter.out.persistence.repository.BbergCurvaPrimrRepository;
import br.com.poc.application.model.BloombergCurvaPrimr;
import br.com.poc.application.port.out.BloombergCurvaPrimrRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class BloombergCurvaPrimrPersistenceAdapter implements BloombergCurvaPrimrRepositoryPort {

    private final BbergCurvaPrimrRepository repository;
    private final BloombergCurvaPrimrMapper mapper;

    @Override
    public BloombergCurvaPrimr save(BloombergCurvaPrimr entity) {
        BloombergCurvaPrimrEntity persistenceEntity = mapper.toEntity(entity);
        if (persistenceEntity.getCIdtfdUnic() == null) {
            persistenceEntity.setCIdtfdUnic(repository.reserveNextIdentifier());
        }

        BloombergCurvaPrimrEntity saved = repository.save(persistenceEntity);
        return mapper.toDomain(saved);
    }

    @Override
    public Optional<BloombergCurvaPrimr> findById(Integer cIdtfdUnic) {
        return repository.findById(cIdtfdUnic)
            .map(mapper::toDomain);
    }

    @Override
    public List<BloombergCurvaPrimr> findAll() {
        return repository.findAll()
            .stream()
            .map(mapper::toDomain)
            .toList();
    }

    @Override
    public List<BloombergCurvaPrimr> findByFilters(String cTickerIndcd, LocalDate dBaseReft) {
        return repository.findByFilters(cTickerIndcd, dBaseReft)
            .stream()
            .map(mapper::toDomain)
            .toList();
    }

    @Override
    public void delete(Integer cIdtfdUnic) {
        repository.deleteById(cIdtfdUnic);
    }

    @Override
    public boolean existsById(Integer cIdtfdUnic) {
        return repository.existsById(cIdtfdUnic);
    }
}
