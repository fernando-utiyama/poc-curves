package br.com.poc.application.port.in.usecase;

import br.com.poc.application.dto.BloombergCurvaPrimrResponse;
import br.com.poc.application.dto.CreateBloombergCurvaPrimrRequest;
import br.com.poc.application.dto.UpdateBloombergCurvaPrimrRequest;

import java.time.LocalDate;
import java.util.List;

public interface BloombergCurvaPrimrUseCase {

    BloombergCurvaPrimrResponse create(CreateBloombergCurvaPrimrRequest request);

    BloombergCurvaPrimrResponse findById(Integer cldtfdUnic);

    List<BloombergCurvaPrimrResponse> findAll();

    List<BloombergCurvaPrimrResponse> findByFilters(String cTickerIndcd, LocalDate dBaseReft);

    BloombergCurvaPrimrResponse update(Integer cldtfdUnic, UpdateBloombergCurvaPrimrRequest request);

    void delete(Integer cldtfdUnic);
}
