package br.com.poc.adapter.out.persistence.jpa.repository;

import br.com.poc.adapter.out.persistence.jpa.entity.DadoCurvaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface DadoCurvaJpaRepository extends JpaRepository<DadoCurvaEntity, DadoCurvaEntity.DadoCurvaId> {
    List<DadoCurvaEntity> findByBaseReftAndTickerIndcdOrderByDiaUtilAsc(LocalDate baseReft, String tickerIndcd);
    List<DadoCurvaEntity> findByBaseReftAndTickerIndcdOrderByQtdDiaReftAsc(LocalDate baseReft, String tickerIndcd);
}
