package br.com.poc.starter.srv.hex.adapter.out.persistence.inmemory.repository;

import br.com.poc.starter.srv.hex.adapter.out.persistence.inmemory.entity.B3CurveRawEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;

/**
 * Repositório Spring Data para a entidade B3CurveRawEntity.
 *
 * Responsável pelo acesso direto à tabela mkt.B3CurveRaw.
 */
@Repository
public interface B3JpaRepository
    extends JpaRepository<B3CurveRawEntity, String> {

    /**
     * Busca registro pelo ticker e data de referência.
     *
     * @param ticker código do ativo
     * @param refDate data de referência
     * @return Optional contendo a entidade caso exista
     */
    Optional<B3CurveRawEntity> findByTickerAndRefDate(
        String ticker,
        LocalDate refDate
    );
}
