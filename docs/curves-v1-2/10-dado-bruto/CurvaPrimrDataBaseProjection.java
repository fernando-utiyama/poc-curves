package br.com.poc.adapter.out.persistence.repository;

import java.time.LocalDate;

/** Uma linha da listagem do dado bruto (curva, data-base e o que existe nela). Usada pelos três repositórios do bruto. */
public interface CurvaPrimrDataBaseProjection {

    String getCodigo();

    String getNome();

    String getSituacao();

    LocalDate getDataRef();

    Long getQuantidade();

    Integer getConstruida();
}
