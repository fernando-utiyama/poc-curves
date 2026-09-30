package br.com.poc.adapter.out.persistence.repository;

import br.com.poc.adapter.out.persistence.entity.ProvedorEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SpringDataProvedorRepository extends JpaRepository<ProvedorEntity, String> {
    @Query("""
        SELECT p
        FROM ProvedorEntity p
        WHERE UPPER(p.nomeProvedor) LIKE UPPER(CONCAT('%', :texto, '%'))
           OR UPPER(p.descricao) LIKE UPPER(CONCAT('%', :texto, '%'))
           OR UPPER(p.produto) LIKE UPPER(CONCAT('%', :texto, '%'))
           OR UPPER(p.nomeCompletoAtivoOuInstrumento) LIKE UPPER(CONCAT('%', :texto, '%'))
        ORDER BY p.nomeProvedor
        """)
    List<ProvedorEntity> filtrarPorTexto(@Param("texto") String texto);
}
