package br.com.poc.adapter.out.persistence.repository;

import br.com.poc.adapter.out.persistence.entity.BtrsCurvaPrimrEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Dado bruto da B3 (tBtrsCurvaPrimr). Mesma forma nos três repositórios do bruto; muda só a tabela e a ordem dos vértices. */
public interface BtrsCurvaPrimrRepository extends JpaRepository<BtrsCurvaPrimrEntity, Integer> {

    @Query(value = "SELECT ISNULL(MAX(cIdtfdUnic), 0) + 1 FROM dbo.tBtrsCurvaPrimr WITH (UPDLOCK, HOLDLOCK)", nativeQuery = true)
    Integer proximoId();

    List<BtrsCurvaPrimrEntity> findByTickerIndcdAndDataBaseReftOrderByDiaCorriAscIdtfdUnicAsc(String nomeCurva, LocalDate dataBase);

    Optional<BtrsCurvaPrimrEntity> findByIdtfdUnicAndTickerIndcdAndDataBaseReft(Integer id, String nomeCurva, LocalDate dataBase);

    void deleteByIdtfdUnicAndTickerIndcdAndDataBaseReft(Integer id, String nomeCurva, LocalDate dataBase);

    int deleteByTickerIndcdAndDataBaseReft(String nomeCurva, LocalDate dataBase);

    @Query(value = "SELECT CASE WHEN EXISTS (SELECT 1 FROM dbo.tDadoVertcCurva v "
        + "WHERE v.cTickerIndcd = :nomeCurva AND v.dBaseReft = :dataBase) THEN 1 ELSE 0 END", nativeQuery = true)
    Integer existeVerticeConstruido(@Param("nomeCurva") String nomeCurva, @Param("dataBase") LocalDate dataBase);

    /** Uma linha por curva e data-base no período. Mostra também as curvas sem código (o GET respeita só o banco). */
    @Query(value = """
        SELECT m.cTickerIdtfdUnic AS codigo,
               m.cTickerIndcd AS nome,
               m.cSitReg AS situacao,
               p.dBaseReft AS dataRef,
               COUNT(*) AS quantidade,
               CASE WHEN EXISTS (SELECT 1 FROM dbo.tDadoVertcCurva v WHERE v.cTickerIndcd = m.cTickerIndcd AND v.dBaseReft = p.dBaseReft) THEN 1 ELSE 0 END AS construida
        FROM dbo.tBtrsCurvaPrimr p
        JOIN dbo.tCurvaMercd m ON m.cTickerIndcd = p.cTickerIndcd
        WHERE p.dBaseReft BETWEEN :de AND :ate
          AND (:codigo IS NULL OR m.cTickerIdtfdUnic = :codigo)
          AND (:nome IS NULL OR UPPER(m.cTickerIndcd) LIKE UPPER(CONCAT('%', :nome, '%')))
        GROUP BY m.cTickerIdtfdUnic, m.cTickerIndcd, m.cSitReg, p.dBaseReft
        ORDER BY p.dBaseReft DESC, m.cTickerIndcd ASC
        """,
        nativeQuery = true)
    List<CurvaPrimrDataBaseProjection> listarDatasBase(
        @Param("de") LocalDate de,
        @Param("ate") LocalDate ate,
        @Param("codigo") String codigo,
        @Param("nome") String nome
    );

    /** Uma linha por curva, com a última data-base gravada no bruto dela. */
    @Query(value = """
        SELECT m.cTickerIdtfdUnic AS codigo,
               m.cTickerIndcd AS nome,
               m.cSitReg AS situacao,
               p.dBaseReft AS dataRef,
               COUNT(*) AS quantidade,
               CASE WHEN EXISTS (SELECT 1 FROM dbo.tDadoVertcCurva v WHERE v.cTickerIndcd = m.cTickerIndcd AND v.dBaseReft = p.dBaseReft) THEN 1 ELSE 0 END AS construida
        FROM dbo.tBtrsCurvaPrimr p
        JOIN dbo.tCurvaMercd m ON m.cTickerIndcd = p.cTickerIndcd
        WHERE p.dBaseReft = (SELECT MAX(u.dBaseReft) FROM dbo.tBtrsCurvaPrimr u WHERE u.cTickerIndcd = p.cTickerIndcd)
          AND (:codigo IS NULL OR m.cTickerIdtfdUnic = :codigo)
          AND (:nome IS NULL OR UPPER(m.cTickerIndcd) LIKE UPPER(CONCAT('%', :nome, '%')))
        GROUP BY m.cTickerIdtfdUnic, m.cTickerIndcd, m.cSitReg, p.dBaseReft
        ORDER BY m.cTickerIndcd ASC
        """,
        nativeQuery = true)
    List<CurvaPrimrDataBaseProjection> listarUltimaDataBase(
        @Param("codigo") String codigo,
        @Param("nome") String nome
    );
}
