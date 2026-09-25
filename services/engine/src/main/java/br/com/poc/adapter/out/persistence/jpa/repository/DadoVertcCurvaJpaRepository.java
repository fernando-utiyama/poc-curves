package br.com.poc.adapter.out.persistence.jpa.repository;

import br.com.poc.adapter.out.persistence.jpa.entity.DadoVertcCurvaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface DadoVertcCurvaJpaRepository extends JpaRepository<DadoVertcCurvaEntity, DadoVertcCurvaEntity.DadoVertcCurvaId> {
    List<DadoVertcCurvaEntity> findByBaseReftAndTickerIndcdOrderByDiaUtilAsc(LocalDate baseReft, String tickerIndcd);
    void deleteByBaseReftAndTickerIndcd(LocalDate baseReft, String tickerIndcd);
}
