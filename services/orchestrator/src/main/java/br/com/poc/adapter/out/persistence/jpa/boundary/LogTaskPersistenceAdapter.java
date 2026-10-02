package br.com.poc.adapter.out.persistence.jpa.boundary;

import br.com.poc.adapter.out.persistence.jpa.mapper.LogTarefaJpaMapper;
import br.com.poc.adapter.out.persistence.jpa.repository.LogTarefaJpaRepository;
import br.com.poc.application.model.scheduler.LogTarefa;
import br.com.poc.application.port.out.LogTaskRepositoryPort;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class LogTaskPersistenceAdapter implements LogTaskRepositoryPort {

    private final LogTarefaJpaRepository repository;
    private final LogTarefaJpaMapper mapper;

    public LogTaskPersistenceAdapter(
        LogTarefaJpaRepository repository,
        LogTarefaJpaMapper mapper
    ) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public List<LogTarefa> buscarPorTarefaId(Long tarefaId) {
        return repository.findByTarefaIdOrderByDataCriacaoDesc(tarefaId)
            .stream()
            .map(mapper::toDomain)
            .toList();
    }

    @Override
    public Map<Long, List<LogTarefa>> buscarPorTarefaIds(List<Long> tarefaIds) {
        if (tarefaIds == null || tarefaIds.isEmpty()) {
            return Map.of();
        }

        List<Integer> ids = tarefaIds.stream()
            .map(Math::toIntExact)
            .toList();

        return repository.findByTarefaIds(ids).stream()
            .collect(Collectors.groupingBy(
                entity -> entity.getTarefa().getId().longValue(),
                LinkedHashMap::new,
                Collectors.mapping(mapper::toDomain, Collectors.toList())
            ));
    }
}
