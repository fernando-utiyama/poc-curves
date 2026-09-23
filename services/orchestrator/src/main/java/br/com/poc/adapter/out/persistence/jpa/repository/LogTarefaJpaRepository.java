package br.com.poc.adapter.out.persistence.jpa.repository;

import br.com.poc.adapter.out.persistence.jpa.entity.LogTarefaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LogTarefaJpaRepository extends JpaRepository<LogTarefaEntity, Long> {
    List<LogTarefaEntity> findByTarefaIdOrderByDataCriacaoDesc(Long tarefaId);
}
