package br.com.poc.adapter.out.persistence.repository;

import br.com.poc.adapter.out.persistence.entity.CurvaMercdEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * As consultas por código e por nome têm @Query porque o Spring Data, ao interpretar o nome do método,
 * resolve a propriedade como "CTickerIdtfdUnic" (C maiúsculo, pelo getter do Lombok) e o Hibernate só
 * conhece o atributo "cTickerIdtfdUnic" (UnknownPathException).
 */
public interface CurvaMercdRepository extends JpaRepository<CurvaMercdEntity, String>, JpaSpecificationExecutor<CurvaMercdEntity> {

    @Query("SELECT e FROM CurvaMercdEntity e WHERE e.cTickerIdtfdUnic = :cTickerIdtfdUnic")
    Optional<CurvaMercdEntity> findByCTickerIdtfdUnic(@Param("cTickerIdtfdUnic") String cTickerIdtfdUnic);

    @Query("SELECT e FROM CurvaMercdEntity e WHERE e.cTickerIndcd = :cTickerIndcd")
    Optional<CurvaMercdEntity> findByCTickerIndcd(@Param("cTickerIndcd") String cTickerIndcd);

    @Query("SELECT CASE WHEN COUNT(e) > 0 THEN true ELSE false END FROM CurvaMercdEntity e WHERE e.cTickerIdtfdUnic = :cTickerIdtfdUnic")
    boolean existsByCTickerIdtfdUnic(@Param("cTickerIdtfdUnic") String cTickerIdtfdUnic);

    @Query("SELECT CASE WHEN COUNT(e) > 0 THEN true ELSE false END FROM CurvaMercdEntity e "
        + "WHERE e.cTickerIdtfdUnic = :cTickerIdtfdUnic AND e.cTickerIndcd <> :cTickerIndcd")
    boolean existsByCTickerIdtfdUnicAndCTickerIndcdNot(@Param("cTickerIdtfdUnic") String cTickerIdtfdUnic,
                                                       @Param("cTickerIndcd") String cTickerIndcd);

    @Query("SELECT e FROM CurvaMercdEntity e WHERE e.cTickerIdtfdUnic IS NOT NULL")
    List<CurvaMercdEntity> findByCTickerIdtfdUnicIsNotNull();

    @Query("SELECT e.cTickerIndcd FROM CurvaMercdEntity e")
    List<String> findAllNomes();
}
