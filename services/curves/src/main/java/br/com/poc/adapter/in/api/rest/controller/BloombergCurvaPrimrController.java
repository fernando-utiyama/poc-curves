package br.com.poc.adapter.in.api.rest.controller;

import br.com.poc.adapter.in.api.rest.dto.BloombergCurvaPrimrResponse;
import br.com.poc.adapter.in.api.rest.dto.CreateBloombergCurvaPrimrRequest;
import br.com.poc.adapter.in.api.rest.dto.UpdateBloombergCurvaPrimrRequest;
import br.com.poc.adapter.in.api.rest.mapper.BloombergCurvaPrimrRestMapper;
import br.com.poc.adapter.in.api.rest.openapi.BloombergCurvaAPI;
import br.com.poc.application.port.in.usecase.BloombergCurvaPrimrUseCase;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequiredArgsConstructor
public class BloombergCurvaPrimrController implements BloombergCurvaAPI {

    private final BloombergCurvaPrimrUseCase useCase;
    private final BloombergCurvaPrimrRestMapper mapper;

    @Override
    public ResponseEntity<BloombergCurvaPrimrResponse> create(@Valid @RequestBody CreateBloombergCurvaPrimrRequest request) {
        BloombergCurvaPrimrResponse response = mapper.toRest(useCase.create(mapper.toApplication(request)));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Override
    public ResponseEntity<BloombergCurvaPrimrResponse> findById(Integer cIdtfdUnic) {
        return ResponseEntity.ok(mapper.toRest(useCase.findById(cIdtfdUnic)));
    }

    @Override
    public ResponseEntity<List<BloombergCurvaPrimrResponse>> findAll() {
        return ResponseEntity.ok(mapper.toRestList(useCase.findAll()));
    }

    @Override
    public ResponseEntity<List<BloombergCurvaPrimrResponse>> findByFilters(String cTickerIndcd, LocalDate dBaseReft) {
        return ResponseEntity.ok(mapper.toRestList(useCase.findByFilters(cTickerIndcd, dBaseReft)));
    }

    @Override
    public ResponseEntity<BloombergCurvaPrimrResponse> update(
            Integer cIdtfdUnic,
            @Valid @RequestBody UpdateBloombergCurvaPrimrRequest request
    ) {
        return ResponseEntity.ok(mapper.toRest(useCase.update(cIdtfdUnic, mapper.toApplication(request))));
    }

    @Override
    public ResponseEntity<Void> delete(Integer cIdtfdUnic) {
        useCase.delete(cIdtfdUnic);
        return ResponseEntity.noContent().build();
    }
}
