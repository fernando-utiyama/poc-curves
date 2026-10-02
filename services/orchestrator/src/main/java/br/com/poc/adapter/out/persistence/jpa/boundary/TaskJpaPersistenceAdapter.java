package br.com.poc.adapter.out.persistence.jpa.boundary;

import br.com.poc.adapter.out.persistence.jpa.mapper.TarefaJpaMapper;
import br.com.poc.adapter.out.persistence.jpa.repository.TarefaJpaRepository;
import br.com.poc.application.model.scheduler.Tarefa;
import br.com.poc.application.port.out.TaskRepositoryPort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Adapter de persistência JPA que implementa `TaskRepositoryPort`.
 *
 * Responsabilidades:
 * - Mapear chamadas de repositório JPA para o modelo de domínio (`Tarefa`) usando
 *   `TarefaJpaMapper`.
 * - Fornecer operações CRUD e consulta de tarefas persistidas para reidratacao do scheduler.
 */
@Component
public class TaskJpaPersistenceAdapter implements TaskRepositoryPort {

    private final TarefaJpaRepository tarefaJpaRepository;
    private final TarefaJpaMapper tarefaJpaMapper;

    public TaskJpaPersistenceAdapter(TarefaJpaRepository tarefaJpaRepository, TarefaJpaMapper tarefaJpaMapper) {
        this.tarefaJpaRepository = tarefaJpaRepository;
        this.tarefaJpaMapper = tarefaJpaMapper;
    }

    @Override
    public Tarefa save(Tarefa tarefa) {
        return tarefaJpaMapper.toDomain(tarefaJpaRepository.save(tarefaJpaMapper.toEntity(tarefa)));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Tarefa> findAllTarefas() { return tarefaJpaMapper.toDomainList(tarefaJpaRepository.findAll()); }

    @Override
    @Transactional(readOnly = true)
    public List<Tarefa> findAllTarefasResumidas() {
        return tarefaJpaRepository.findAll().stream()
            .map(tarefaJpaMapper::toDomainNoParameters)
            .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Tarefa> findPersistedScheduledTarefas() {
        return tarefaJpaMapper.toDomainList(tarefaJpaRepository.findPersistedScheduled());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Tarefa> findTarefaById(Long id) {
        return tarefaJpaRepository.findById(id).map(tarefaJpaMapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Tarefa> findTarefaByNome(String nome) {
        return tarefaJpaRepository.findByIdentificadorParametroIgnoreCase(nome).map(tarefaJpaMapper::toDomain);
    }

    @Override
    public void deleteTarefaById(Long id) { tarefaJpaRepository.deleteById(id); }

    @Override
    public Optional<Tarefa> buscarPorId(Long id) {
        return tarefaJpaRepository.findById(id)
            .map(tarefaJpaMapper::toDomainNoParameters);
    }
}
