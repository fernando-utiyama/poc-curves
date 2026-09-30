package br.com.poc.adapter.in.api.rest.mapper;

import br.com.poc.adapter.in.api.rest.dto.BloombergCurvaPrimrResponse;
import br.com.poc.adapter.in.api.rest.dto.CreateBloombergCurvaPrimrRequest;
import br.com.poc.adapter.in.api.rest.dto.UpdateBloombergCurvaPrimrRequest;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface BloombergCurvaPrimrRestMapper {

    br.com.poc.application.dto.CreateBloombergCurvaPrimrRequest toApplication(CreateBloombergCurvaPrimrRequest request);

    br.com.poc.application.dto.UpdateBloombergCurvaPrimrRequest toApplication(UpdateBloombergCurvaPrimrRequest request);

    BloombergCurvaPrimrResponse toRest(br.com.poc.application.dto.BloombergCurvaPrimrResponse response);

    List<BloombergCurvaPrimrResponse> toRestList(List<br.com.poc.application.dto.BloombergCurvaPrimrResponse> responses);
}
