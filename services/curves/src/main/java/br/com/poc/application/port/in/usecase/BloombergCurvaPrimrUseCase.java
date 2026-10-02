package br.com.poc.application.port.in.usecase;

import br.com.poc.application.dto.BloombergCurvaPrimrResponse;
import br.com.poc.application.dto.CreateBloombergCurvaPrimrRequest;
import br.com.poc.application.dto.UpdateBloombergCurvaPrimrRequest;

import java.time.LocalDate;
import java.util.List;

public interface BloombergCurvaPrimrUseCase {

    BloombergCurvaPrimrResponse create(CreateBloombergCurvaPrimrRequest request);

    BloombergCurvaPrimrResponse findById(Integer cIdtfdUnic);

    List<BloombergCurvaPrimrResponse> findAll();

    List<BloombergCurvaPrimrResponse> findByFilters(String cTickerIndcd, LocalDate dBaseReft);

    BloombergCurvaPrimrResponse update(Integer cIdtfdUnic, UpdateBloombergCurvaPrimrRequest request);

    void delete(Integer cIdtfdUnic);
}
