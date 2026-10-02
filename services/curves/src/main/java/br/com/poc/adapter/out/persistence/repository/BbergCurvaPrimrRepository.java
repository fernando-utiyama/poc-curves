package br.com.poc.adapter.out.persistence.repository;

import br.com.poc.adapter.out.persistence.entity.BloombergCurvaPrimrEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;

public interface BbergCurvaPrimrRepository extends JpaRepository<BloombergCurvaPrimrEntity, Integer> {

    @Query(value = """
        SELECT ISNULL(MAX(cIdtfdUnic), 0) + 1
        FROM dbo.tBbergCurvaPrimr WITH (UPDLOCK, HOLDLOCK)
        """, nativeQuery = true)
    Integer reserveNextIdentifier();

    @Query("""
        SELECT entity
        FROM BloombergCurvaPrimrEntity entity
        WHERE entity.cTickerIndcd = :cTickerIndcd
          AND entity.dBaseReft = :dBaseReft
        ORDER BY entity.dVctoContr ASC
        """)
    List<BloombergCurvaPrimrEntity> findByFilters(
        @Param("cTickerIndcd") String cTickerIndcd,
        @Param("dBaseReft") LocalDate dBaseReft
    );
}
