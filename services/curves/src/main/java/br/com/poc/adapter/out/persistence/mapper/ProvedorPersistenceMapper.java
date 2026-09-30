package br.com.poc.adapter.out.persistence.mapper;

import br.com.poc.adapter.out.persistence.entity.ProvedorEntity;
import br.com.poc.application.model.Provedor;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ProvedorPersistenceMapper {

    ProvedorEntity toEntity(Provedor provedor);

    Provedor toDomain(ProvedorEntity entity);
}
