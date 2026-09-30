package br.com.poc.adapter.out.persistence;

import br.com.poc.adapter.out.persistence.repository.CurvaMercdRepository;
import br.com.poc.application.port.out.CurvaMercdRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CurvaMercdPersistenceAdapter implements CurvaMercdRepositoryPort {

    private final CurvaMercdRepository repository;

    @Override
    public boolean existsByTicker(String cTickerIndcd) {
        return repository.existsById(cTickerIndcd);
    }
}
