package br.com.poc.adapter.in.api.rest.mapper;

import br.com.poc.adapter.in.api.rest.dto.v1.ProvedorResponse;
import br.com.poc.application.dto.v1.ProvedorResult;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ProvedorApiMapper {

    ProvedorResponse toResponse(ProvedorResult result);
}
