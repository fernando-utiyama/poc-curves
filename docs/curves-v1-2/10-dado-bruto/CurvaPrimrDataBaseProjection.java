package br.com.poc.adapter.out.persistence.repository;

import java.time.LocalDate;

/** Linha da listagem do dado bruto. */
public interface CurvaPrimrDataBaseProjection {

    String getCodigo();

    String getNome();

    String getSituacao();

    LocalDate getDataRef();

    Long getQuantidade();

    Integer getConstruida();
}
