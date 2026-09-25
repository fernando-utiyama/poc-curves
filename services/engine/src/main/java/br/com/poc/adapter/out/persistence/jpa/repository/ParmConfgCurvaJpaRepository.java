package br.com.poc.adapter.out.persistence.jpa.repository;

import br.com.poc.adapter.out.persistence.jpa.entity.ParmConfgCurvaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ParmConfgCurvaJpaRepository extends JpaRepository<ParmConfgCurvaEntity, ParmConfgCurvaEntity.ParmConfgCurvaId> {
    List<ParmConfgCurvaEntity> findByIdtfdConfg(Integer idtfdConfg);
}
