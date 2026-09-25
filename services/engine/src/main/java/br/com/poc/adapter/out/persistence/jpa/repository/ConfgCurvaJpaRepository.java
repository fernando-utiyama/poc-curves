package br.com.poc.adapter.out.persistence.jpa.repository;

import br.com.poc.adapter.out.persistence.jpa.entity.ConfgCurvaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ConfgCurvaJpaRepository extends JpaRepository<ConfgCurvaEntity, Integer> {
    Optional<ConfgCurvaEntity> findByTickerIndcd(String tickerIndcd);
}
