package br.com.poc.adapter.in.api.rest.mapper.scheduler;

import br.com.poc.adapter.in.api.rest.dto.scheduler.SchedulerStatusDto;
import br.com.poc.adapter.in.api.rest.dto.scheduler.SchedulerTaskStatusDto;
import br.com.poc.application.model.scheduler.SchedulerStatus;
import br.com.poc.application.model.scheduler.SchedulerTaskStatus;
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;

@Mapper(
    componentModel = "spring",
    injectionStrategy = InjectionStrategy.CONSTRUCTOR
)
/**
 * Mapper MapStruct para conversão entre `SchedulerStatus` (domínio) e
 * `SchedulerStatusDto` (API). Facilita a separação entre representação interna
 * e a representação exposta pela API REST.
 */
public interface SchedulerMapper {

    SchedulerStatusDto toDto(SchedulerStatus schedulerStatus);

    SchedulerTaskStatusDto toDto(SchedulerTaskStatus schedulerTaskStatus);
}
