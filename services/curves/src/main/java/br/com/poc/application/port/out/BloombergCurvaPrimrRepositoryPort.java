package br.com.poc.application.port.out;

import br.com.poc.application.model.BloombergCurvaPrimr;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface BloombergCurvaPrimrRepositoryPort {

    BloombergCurvaPrimr save(BloombergCurvaPrimr entity);

    Optional<BloombergCurvaPrimr> findById(Integer cldtfdUnic);

    List<BloombergCurvaPrimr> findAll();

    List<BloombergCurvaPrimr> findByFilters(String cTickerIndcd, LocalDate dBaseReft);

    void delete(Integer cldtfdUnic);

    boolean existsById(Integer cldtfdUnic);
}
