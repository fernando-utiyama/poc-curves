package br.com.poc.adapter.out.persistence.jpa.repository;

import br.com.poc.adapter.out.persistence.jpa.entity.TarefaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

/**
 * Repositório JPA para a entidade `TarefaEntity`.
 *
 * Fornece consultas adicionais usadas pelo scheduler, como a busca por tarefas
 * persistidas que devem ser reidratadas no agendador (`findPersistedScheduled`).
 */
public interface TarefaJpaRepository extends JpaRepository<TarefaEntity, Long> {
    Optional<TarefaEntity> findByIdentificadorParametroIgnoreCase(String nome);

    // `status is null` cobre registros legados que ainda nao foram normalizados.
    @Query("select t from TarefaEntity t where t.situacao is null or t.situacao = 'AGENDADA'")
    List<TarefaEntity> findPersistedScheduled();
}
