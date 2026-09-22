package br.com.poc.starter.srv.hex.adapter.out.persistence.inmemory.repository;

import br.com.poc.starter.srv.hex.adapter.out.persistence.inmemory.entity.BloombergCurveRawEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;

/**
 * Repositório Spring Data para a entidade BloombergCurveRawEntity.
 *
 * Responsável pelo acesso direto à tabela mkt.BloombergCurveRaw.
 */
@Repository
public interface BloombergJpaRepository
    extends JpaRepository<BloombergCurveRawEntity, String> {

    /**
     * Busca registro pelo ticker e data de referência.
     *
     * @param ticker código do ativo
     * @param refDate data de referência
     * @return Optional contendo a entidade caso exista
     */
    Optional<BloombergCurveRawEntity> findByTickerAndRefDate(
        String ticker,
        LocalDate refDate
    );
}
