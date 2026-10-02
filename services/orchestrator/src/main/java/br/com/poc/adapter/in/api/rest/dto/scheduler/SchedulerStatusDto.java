package br.com.poc.adapter.in.api.rest.dto.scheduler;

import java.util.List;
import java.util.Map;

public record SchedulerStatusDto(
    Map<String, Boolean> runningTasks,
    List<SchedulerTaskStatusDto> tasks
) {
}
