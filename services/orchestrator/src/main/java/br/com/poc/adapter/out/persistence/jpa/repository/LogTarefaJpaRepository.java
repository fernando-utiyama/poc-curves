package br.com.poc.adapter.out.persistence.jpa.repository;

import br.com.poc.adapter.out.persistence.jpa.entity.LogTarefaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface LogTarefaJpaRepository extends JpaRepository<LogTarefaEntity, Long> {
    List<LogTarefaEntity> findByTarefaIdOrderByDataCriacaoDesc(Long tarefaId);

    @Query("""
        select log
        from LogTarefaEntity log
        where log.tarefa.id in :tarefaIds
        order by log.tarefa.id asc, log.dataCriacao desc
        """)
    List<LogTarefaEntity> findByTarefaIds(@Param("tarefaIds") List<Integer> tarefaIds);
}
