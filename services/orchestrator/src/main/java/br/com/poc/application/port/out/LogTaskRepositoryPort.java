package br.com.poc.application.port.out;

import br.com.poc.application.model.scheduler.LogTarefa;

import java.util.List;

public interface LogTaskRepositoryPort {
    List<LogTarefa> buscarPorTarefaId(Long tarefaId);
}
