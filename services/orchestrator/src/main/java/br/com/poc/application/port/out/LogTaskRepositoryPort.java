package br.com.poc.application.port.out;

import br.com.poc.application.model.scheduler.LogTarefa;

import java.util.List;
import java.util.Map;

public interface LogTaskRepositoryPort {
    List<LogTarefa> buscarPorTarefaId(Long tarefaId);
    Map<Long, List<LogTarefa>> buscarPorTarefaIds(List<Long> tarefaIds);
}
