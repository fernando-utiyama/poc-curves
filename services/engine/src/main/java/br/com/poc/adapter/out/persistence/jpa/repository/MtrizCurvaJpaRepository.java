package br.com.poc.adapter.out.persistence.jpa.repository;

import br.com.poc.adapter.out.persistence.jpa.entity.MtrizCurvaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;

@Repository
public interface MtrizCurvaJpaRepository extends JpaRepository<MtrizCurvaEntity, MtrizCurvaEntity.MtrizCurvaId> {
    Optional<MtrizCurvaEntity> findByTickerIndcdAndBaseReft(String tickerIndcd, LocalDate baseReft);
}
