package br.com.poc.adapter.out.persistence.jpa.mapper;

import br.com.poc.adapter.out.persistence.jpa.entity.LogTarefaEntity;
import br.com.poc.adapter.out.persistence.jpa.entity.ParametroTarefaEntity;
import br.com.poc.adapter.out.persistence.jpa.entity.TarefaEntity;
import br.com.poc.application.model.scheduler.LogTarefa;
import br.com.poc.application.model.scheduler.ParametroTarefa;
import br.com.poc.application.model.scheduler.Tarefa;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Mapper responsável por converter entre as entidades JPA (`TarefaEntity`,
 * `ParametroTarefaEntity`, `LogTarefaEntity`) e os modelos de domínio (`Tarefa`,
 * `ParametroTarefa`, `LogTarefa`).
 *
 * Observações:
 * - Realiza conversões de tipos (Integer <-> Long) e mapeia coleções.
 */
@Component
public class TarefaJpaMapper {

    public Tarefa toDomain(TarefaEntity entity) {
        if (entity == null) {
            return null;
        }

        Tarefa tarefa = new Tarefa();
        tarefa.setId(toLong(entity.getId()));
        tarefa.setNome(entity.getIdentificadorParametro());
        tarefa.setDescricao(entity.getAcaoOperSistema());
        tarefa.setAction(entity.getDescricao());
        tarefa.setRegraCron(entity.getRegraAgendamento());
        tarefa.setRegraIntervalo(entity.getRegraIntervalo());
        tarefa.setStatus(entity.getSituacao());
        tarefa.setParametros(
            entity.getParametros().stream()
                .map(this::toDomain)
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new))
        );
        tarefa.setLogs(
            entity.getLogs().stream()
                .map(this::toDomain)
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new))
        );
        return tarefa;
    }

    public TarefaEntity toEntity(Tarefa tarefa) {
        TarefaEntity entity = new TarefaEntity();
        entity.setId(toInteger(tarefa.getId()));
        entity.setIdentificadorParametro(tarefa.getNome());
        entity.setAcaoOperSistema(tarefa.getDescricao());
        entity.setDescricao(tarefa.getAction());
        entity.setRegraAgendamento(tarefa.getRegraCron());
        entity.setRegraIntervalo(tarefa.getRegraIntervalo());
        entity.setSituacao(tarefa.getStatus());
        entity.setParametros(new LinkedHashSet<>());
        entity.setLogs(new ArrayList<>());
        tarefa.getParametros().forEach(parametro -> entity.getParametros().add(toEntity(parametro, entity)));
        tarefa.getLogs().forEach(log -> entity.getLogs().add(toEntity(log, entity)));
        return entity;
    }

    public List<Tarefa> toDomainList(List<TarefaEntity> entities) {
        return entities.stream().map(this::toDomain).toList();
    }

    private ParametroTarefa toDomain(ParametroTarefaEntity entity) {
        ParametroTarefa parametro = new ParametroTarefa();
        parametro.setId(toLong(entity.getIdParametro()));
        parametro.setNome(entity.getCategoriaConfiguracao());
        parametro.setValor(entity.getValorParametro());
        parametro.setTipo(entity.getIdentificadorParametro());
        return parametro;
    }

    private LogTarefa toDomain(LogTarefaEntity entity) {
        LogTarefa log = new LogTarefa();
        log.setId(toLong(entity.getIdEntrada()));
        log.setDataCriacao(entity.getDataCriacao());
        log.setCodigo(entity.getCodigo());
        log.setTexto(entity.getDescricaoLog());
        return log;
    }

    private ParametroTarefaEntity toEntity(ParametroTarefa parametro, TarefaEntity tarefa) {
        ParametroTarefaEntity entity = new ParametroTarefaEntity();
        entity.setIdParametro(toInteger(parametro.getId()));
        entity.setTarefa(tarefa);
        entity.setCategoriaConfiguracao(parametro.getNome());
        entity.setValorParametro(parametro.getValor());
        entity.setIdentificadorParametro(parametro.getTipo());
        return entity;
    }

    private LogTarefaEntity toEntity(LogTarefa log, TarefaEntity tarefa) {
        LogTarefaEntity entity = new LogTarefaEntity();
        entity.setIdEntrada(toInteger(log.getId()));
        entity.setTarefa(tarefa);
        entity.setDataCriacao(log.getDataCriacao());
        entity.setCodigo(log.getCodigo());
        entity.setDescricaoLog(log.getTexto());
        return entity;
    }

    public Tarefa toDomainNoParameters(TarefaEntity entity) {
        if (entity == null) {
            return null;
        }

        Tarefa tarefa = new Tarefa();
        tarefa.setId(toLong(entity.getId()));
        tarefa.setNome(entity.getIdentificadorParametro());
        tarefa.setDescricao(entity.getAcaoOperSistema());
        tarefa.setAction(entity.getDescricao());
        tarefa.setRegraCron(entity.getRegraAgendamento());
        tarefa.setRegraIntervalo(entity.getRegraIntervalo());
        tarefa.setStatus(entity.getSituacao());
        return tarefa;
    }


    private Long toLong(Integer value) { return value == null ? null : value.longValue(); }

    private Integer toInteger(Long value) { return value == null ? null : Math.toIntExact(value); }
}
