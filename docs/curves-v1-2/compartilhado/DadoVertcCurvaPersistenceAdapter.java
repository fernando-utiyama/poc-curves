package br.com.poc.adapter.out.persistence;

import br.com.poc.application.port.out.DadoVertcCurvaRepositoryPort;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/** Tabelas do engine: a curves só pergunta se existe e só apaga por data, nunca grava. */
@Component
public class DadoVertcCurvaPersistenceAdapter implements DadoVertcCurvaRepositoryPort {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public boolean existeConstrucao(String nomeCurva, LocalDate de, LocalDate ate) {
        Number existe = (Number) entityManager.createNativeQuery(
                "SELECT CASE WHEN EXISTS (SELECT 1 FROM dbo.tDadoVertcCurva "
                    + "WHERE cTickerIndcd = :nome AND dBaseReft BETWEEN :de AND :ate) THEN 1 ELSE 0 END")
            .setParameter("nome", nomeCurva)
            .setParameter("de", de)
            .setParameter("ate", ate)
            .getSingleResult();
        return existe.intValue() == 1;
    }

    /** Precisa de transação ativa (o serviço é @Transactional). A interpolada sai primeiro, para ninguém ler interpolada de vértices que já saíram. */
    @Override
    public boolean apagar(String nomeCurva, LocalDate dataBase) {
        int linhas = apagarDe("dbo.tDadoCurva", nomeCurva, dataBase) + apagarDe("dbo.tDadoVertcCurva", nomeCurva, dataBase);
        return linhas > 0;
    }

    private int apagarDe(String tabela, String nomeCurva, LocalDate dataBase) {
        return entityManager.createNativeQuery(
                "DELETE FROM " + tabela + " WHERE cTickerIndcd = :nome AND dBaseReft = :data")
            .setParameter("nome", nomeCurva)
            .setParameter("data", dataBase)
            .executeUpdate();
    }
}
