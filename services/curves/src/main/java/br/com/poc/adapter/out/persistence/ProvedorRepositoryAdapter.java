package br.com.poc.adapter.out.persistence;

import br.com.poc.adapter.out.persistence.mapper.ProvedorPersistenceMapper;
import br.com.poc.adapter.out.persistence.repository.SpringDataProvedorRepository;
import br.com.poc.application.model.Provedor;
import br.com.poc.application.port.out.ProvedorRepositoryPort;
import java.util.List;
import java.util.Optional;

import br.com.poc.shared.api.util.NormalizadorUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ProvedorRepositoryAdapter implements ProvedorRepositoryPort {

    private final SpringDataProvedorRepository repository;
    private final ProvedorPersistenceMapper mapper;

    @Override
    public boolean existsById(String nomeProvedor) {
        return repository.existsById(nomeProvedor);
    }

    @Override
    public Provedor save(Provedor provedor) {
        return mapper.toDomain(repository.save(mapper.toEntity(provedor)));
    }

    @Override
    public Optional<Provedor> findById(String nomeProvedor) {
        return repository.findById(nomeProvedor).map(mapper::toDomain);
    }

    @Override
    public List<Provedor> findAll() {
        return repository.findAll().stream().map(mapper::toDomain).toList();
    }

    @Override
    public void deleteById(String nomeProvedor) {
        repository.deleteById(nomeProvedor);
    }

    @Override
    public List<Provedor> filtrarPorTexto(String texto) {
        return repository
            .filtrarPorTexto(NormalizadorUtil.normalizarTextoObrigatorio(texto))
            .stream().map(mapper::toDomain).toList();
    }
}
