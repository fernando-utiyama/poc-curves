package br.com.poc.adapter.out.persistence.mapper;

import br.com.poc.adapter.out.persistence.entity.CurvaMercdEntity;
import br.com.poc.application.model.CurvaMercd;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface CurvaMercdMapper {

    CurvaMercd toDomain(CurvaMercdEntity entity);

    CurvaMercdEntity toEntity(CurvaMercd domain);
}
