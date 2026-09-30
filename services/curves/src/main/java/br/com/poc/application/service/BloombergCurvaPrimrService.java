package br.com.poc.application.service;

import br.com.poc.application.dto.BloombergCurvaPrimrResponse;
import br.com.poc.application.dto.CreateBloombergCurvaPrimrRequest;
import br.com.poc.application.dto.UpdateBloombergCurvaPrimrRequest;
import br.com.poc.application.exception.BusinessErrorCode;
import br.com.poc.application.exception.BusinessException;
import br.com.poc.application.exception.InfraErrorCode;
import br.com.poc.application.exception.NotFoundException;
import br.com.poc.application.mapper.BloombergCurvaPrimrRequestMapper;
import br.com.poc.application.model.BloombergCurvaPrimr;
import br.com.poc.application.port.in.usecase.BloombergCurvaPrimrUseCase;
import br.com.poc.application.port.out.BloombergCurvaPrimrRepositoryPort;
import br.com.poc.application.port.out.CurvaMercdRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class BloombergCurvaPrimrService implements BloombergCurvaPrimrUseCase {

    private static final String RECORD_NOT_FOUND_MESSAGE = "Registro não encontrado.";

    private final BloombergCurvaPrimrRepositoryPort repositoryPort;
    private final CurvaMercdRepositoryPort curvaMercdRepositoryPort;
    private final BloombergCurvaPrimrRequestMapper mapper;

    @Override
    public BloombergCurvaPrimrResponse create(CreateBloombergCurvaPrimrRequest request) {
        if (!curvaMercdRepositoryPort.existsByTicker(request.cTickerIndcd())) {
            throw new BusinessException(BusinessErrorCode.CURVA_MERCD_NOT_FOUND);
        }

        BloombergCurvaPrimr domain = mapper.toDomain(request);

        return mapper.toResponse(repositoryPort.save(domain));
    }

    @Override
    @Transactional(readOnly = true)
    public BloombergCurvaPrimrResponse findById(Integer cldtfdUnic) {
        BloombergCurvaPrimr domain = findExistingById(cldtfdUnic);
        return mapper.toResponse(domain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<BloombergCurvaPrimrResponse> findAll() {
        return repositoryPort.findAll()
            .stream()
            .map(mapper::toResponse)
            .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<BloombergCurvaPrimrResponse> findByFilters(String cTickerIndcd, LocalDate dBaseReft) {
        return repositoryPort.findByFilters(cTickerIndcd, dBaseReft)
            .stream()
            .map(mapper::toResponse)
            .toList();
    }

    @Override
    public BloombergCurvaPrimrResponse update(Integer cldtfdUnic, UpdateBloombergCurvaPrimrRequest request) {
        BloombergCurvaPrimr domain = findExistingById(cldtfdUnic);
        mapper.updateDomainFromRequest(request, domain);

        return mapper.toResponse(repositoryPort.save(domain));
    }

    @Override
    public void delete(Integer cldtfdUnic) {
        findExistingById(cldtfdUnic);
        repositoryPort.delete(cldtfdUnic);
    }

    private BloombergCurvaPrimr findExistingById(Integer cldtfdUnic) {
        return repositoryPort.findById(cldtfdUnic)
            .orElseThrow(this::notFoundException);
    }

    private void validateTickerIndcd(String cTickerIndcd) {
        if (cTickerIndcd != null && !curvaMercdRepositoryPort.existsByTicker(cTickerIndcd)) {
            throw new BusinessException(BusinessErrorCode.CURVA_MERCD_NOT_FOUND);
        }
    }

    private NotFoundException notFoundException() {
        return new NotFoundException(InfraErrorCode.NOT_FOUND.getCode(), RECORD_NOT_FOUND_MESSAGE);
    }
}
