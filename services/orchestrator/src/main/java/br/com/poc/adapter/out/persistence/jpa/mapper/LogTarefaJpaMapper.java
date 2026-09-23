package br.com.poc.adapter.out.persistence.jpa.mapper;

import br.com.poc.adapter.out.persistence.jpa.entity.LogTarefaEntity;
import br.com.poc.application.model.scheduler.LogTarefa;
import org.springframework.stereotype.Component;

@Component
public class LogTarefaJpaMapper {
    public LogTarefa toDomain(LogTarefaEntity entity) {
        if (entity == null) {
            return null;
        }

        LogTarefa log = new LogTarefa();
        Integer idEntrada = entity.getIdEntrada();
        log.setId(idEntrada == null ? null : Long.valueOf(idEntrada));
        log.setDataCriacao(entity.getDataCriacao());
        log.setCodigo(entity.getCodigo() != null ? entity.getCodigo() : null);
        log.setTexto(entity.getDescricaoLog());
        return log;
    }
}
