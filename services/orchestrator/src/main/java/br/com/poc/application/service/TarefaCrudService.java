package br.com.poc.application.service;

import br.com.poc.application.exception.InvalidInputException;
import br.com.poc.application.exception.NotFoundException;
import br.com.poc.application.model.scheduler.LogTarefa;
import br.com.poc.application.model.scheduler.SchedulerTaskStatus;
import br.com.poc.application.model.scheduler.Tarefa;
import br.com.poc.application.model.scheduler.TarefaStatus;
import br.com.poc.application.port.in.SchedulerPort;
import br.com.poc.application.port.in.TarefaCrudUseCase;
import br.com.poc.application.port.out.TaskRepositoryPort;
import lombok.AllArgsConstructor;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
public class TarefaCrudService implements TarefaCrudUseCase {

    private static final String ORIGIN = "TAREFA_CRUD";
    public static final String UTC_3 = "UTC-3";
    private static final ZoneId APP_ZONE = ZoneId.of(UTC_3);
    private static final Set<String> PARAMETROS_TIPO_ROBO = Set.of("tiporobo", "robo", "tipo");

    private final TaskRepositoryPort taskRepositoryPort;
    private final SchedulerPort schedulerPort;
    private final TaskActionExecutor taskActionExecutor;
    private final TarefaStatusTransitionService transitionService;

    @Override
    @Transactional
    public Tarefa criarTarefa(Tarefa tarefa) {
        validarCriacao(tarefa);

        tarefa.setStatus(transitionService.transition(null, TarefaStatus.PRONTA).name());

        tarefa.setDataCriacao(LocalDateTime.now(APP_ZONE));
        tarefa.setDataAtualizacao(LocalDateTime.now(APP_ZONE));
        tarefa.setUltimaExecucao(null);
        tarefa.setProximaExecucao(null);
        tarefa.setUltimaMensagem("Tarefa criada com status PRONTA");

        adicionarLog(tarefa, 201, "Tarefa criada com status PRONTA");
        return taskRepositoryPort.save(tarefa);
    }


    @Override
    @Transactional(readOnly = true)
    public List<Tarefa> listarTarefas() {
        List<Tarefa> tarefas = taskRepositoryPort.findAllTarefas();
        preencherExecucoesPorTipoRobo(tarefas);
        return tarefas;
    }

    @Override
    @Transactional(readOnly = true)
    public Tarefa buscarTarefa(Long id) { return buscarPorId(id); }

    @Override
    @Transactional
    public Tarefa atualizarParcialmente(Long id, Tarefa tarefaPatch) {
        if (tarefaPatch == null) {
            throw new InvalidInputException(ORIGIN, "atualizarParcialmente", "Patch da tarefa nao informado");
        }

        Tarefa atual = buscarPorId(id);

        if (tarefaPatch.getNome() != null) {
            atual.setNome(tarefaPatch.getNome());
        }
        if (tarefaPatch.getDescricao() != null) {
            atual.setDescricao(tarefaPatch.getDescricao());
        }
        if (tarefaPatch.getAction() != null) {
            if (!taskActionExecutor.supports(tarefaPatch.getAction())) {
                throw new InvalidInputException(ORIGIN, "atualizarParcialmente", "Action nao suportada: " + tarefaPatch.getAction());
            }
            atual.setAction(tarefaPatch.getAction());
        }
        if (tarefaPatch.getRegraCron() != null) {
            atual.setRegraCron(tarefaPatch.getRegraCron());
        }
        if (tarefaPatch.getRegraIntervalo() != null) {
            atual.setRegraIntervalo(tarefaPatch.getRegraIntervalo());
        }
        if (tarefaPatch.getParametros() != null) {
            atual.setParametros(tarefaPatch.getParametros());
        }

        atual.setDataAtualizacao(LocalDateTime.now(APP_ZONE));
        atual.setUltimaMensagem("Tarefa atualizada parcialmente com sucesso");

        adicionarLog(atual, 200, "Tarefa atualizada parcialmente com sucesso");
        return taskRepositoryPort.save(atual);
    }

    @Override
    @Transactional
    public void desabilitarTarefa(Long id) {
        Tarefa atual = buscarPorId(id);
        validarNaoExecutando(id);

        TarefaStatus statusAtual = TarefaStatus.fromValue(atual.getStatus());
        atual.setStatus(transitionService.transition(statusAtual, TarefaStatus.DESABILITADA).name());
        atual.setDataAtualizacao(LocalDateTime.now(APP_ZONE));
        atual.setUltimaMensagem("Tarefa desabilitada logicamente");

        adicionarLog(atual, 200, "Tarefa desabilitada logicamente");
        taskRepositoryPort.save(atual);
    }

    @Override
    @Transactional
    public void habilitarTarefa(Long id) {
        Tarefa atual = buscarPorId(id);

        TarefaStatus statusAtual = TarefaStatus.fromValue(atual.getStatus());
        atual.setStatus(transitionService.transition(statusAtual, TarefaStatus.PRONTA).name());
        atual.setDataAtualizacao(LocalDateTime.now(APP_ZONE));
        atual.setUltimaMensagem("Tarefa habilitada com sucesso");

        adicionarLog(atual, 200, "Tarefa habilitada com sucesso");
        taskRepositoryPort.save(atual);
    }

    @Override
    @Transactional
    public void removerTarefa(Long id) {
        Tarefa atual = buscarPorId(id);

        TarefaStatus statusAtual = TarefaStatus.fromValue(atual.getStatus());
        atual.setStatus(transitionService.transition(statusAtual, TarefaStatus.REMOVIDA).name());
        atual.setDataAtualizacao(LocalDateTime.now(APP_ZONE));
        atual.setUltimaMensagem("Tarefa removida logicamente");

        adicionarLog(atual, 200, "Tarefa removida logicamente");
        taskRepositoryPort.save(atual);
    }

    private void validarNaoExecutando(Long id) {
        SchedulerTaskStatus schedulerStatus = schedulerPort.getTaskStatus(schedulerKey(id));
        if (schedulerStatus != null && TarefaStatus.EXECUTANDO.name().equalsIgnoreCase(schedulerStatus.getStatus())) {
            throw new InvalidInputException(ORIGIN, "validarNaoExecutando", "Nao e permitido alterar tarefa em execucao");
        }
    }

    private Tarefa buscarPorId(Long id) {
        return taskRepositoryPort.findTarefaById(id)
            .orElseThrow(() -> new NotFoundException("TAREFA_NAO_ENCONTRADA", "Tarefa nao encontrada"));
    }

    private void validarCriacao(Tarefa tarefa) {
        if (tarefa == null) {
            throw new InvalidInputException(ORIGIN, "criarTarefa", "Tarefa nao informada");
        }
        if (isBlank(tarefa.getNome()) || isBlank(tarefa.getDescricao()) || isBlank(tarefa.getAction())) {
            throw new InvalidInputException(ORIGIN, "criarTarefa", "Nome, descricao e action sao obrigatorios");
        }
        if (!taskActionExecutor.supports(tarefa.getAction())) {
            throw new InvalidInputException(ORIGIN, "criarTarefa", "Action nao suportada: " + tarefa.getAction());
        }
        if (!isBlank(tarefa.getRegraCron()) && !CronExpression.isValidExpression(tarefa.getRegraCron())) {
            throw new InvalidInputException(ORIGIN, "criarTarefa", "regraCron invalida: " + tarefa.getRegraCron());
        }
    }

    private void adicionarLog(Tarefa tarefa, Integer codigo, String texto) {
        LogTarefa log = new LogTarefa();
        log.setCodigo(codigo);
        log.setTexto(texto);
        log.setDataCriacao(LocalDateTime.now(APP_ZONE));
        tarefa.getLogs().add(log);
    }

    private void preencherExecucoesPorTipoRobo(List<Tarefa> tarefas) {
        if (tarefas == null || tarefas.isEmpty()) {
            return;
        }

        Map<String, List<Tarefa>> tarefasPorTipoRobo = tarefas.stream()
            .filter(Objects::nonNull)
            .collect(Collectors.groupingBy(this::tipoRobo, Collectors.toCollection(ArrayList::new)));

        tarefasPorTipoRobo.values().forEach(this::preencherExecucoesDoGrupo);
    }

    private void preencherExecucoesDoGrupo(List<Tarefa> tarefas) {
        tarefas.sort(
            Comparator.comparing(this::dataCadastro, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(Tarefa::getId, Comparator.nullsLast(Comparator.naturalOrder()))
        );

        for (int index = 0; index < tarefas.size(); index++) {
            Tarefa tarefa = tarefas.get(index);
            tarefa.setUltimaExecucao(index == 0 ? null : toInstant(dataCadastro(tarefas.get(index - 1))));
            tarefa.setProximaExecucao(index == tarefas.size() - 1 ? null : toInstant(dataCadastro(tarefas.get(index + 1))));
        }
    }

    private String tipoRobo(Tarefa tarefa) {
        if (tarefa.getParametros() != null) {
            for (var parametro : tarefa.getParametros()) {
                if (parametro != null
                    && PARAMETROS_TIPO_ROBO.contains(normalizar(parametro.getNome()))
                    && !isBlank(parametro.getValor())) {
                    return normalizar(parametro.getValor());
                }
            }
        }

        return normalizar(tarefa.getNome());
    }

    private LocalDateTime dataCadastro(Tarefa tarefa) {
        if (tarefa == null) {
            return null;
        }
        if (tarefa.getDataCriacao() != null) {
            return tarefa.getDataCriacao();
        }
        if (tarefa.getLogs() == null) {
            return null;
        }

        return tarefa.getLogs().stream()
            .filter(Objects::nonNull)
            .map(LogTarefa::getDataCriacao)
            .filter(Objects::nonNull)
            .min(Comparator.naturalOrder())
            .orElse(null);
    }

    private Instant toInstant(LocalDateTime data) { return data == null ? null : data.atZone(APP_ZONE).toInstant(); }

    private String normalizar(String value) {
        if (value == null) {
            return "";
        }

        String semAcento = Normalizer.normalize(value, Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "");
        return semAcento.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private boolean isBlank(String value) { return value == null || value.isBlank(); }

    private String schedulerKey(Long id) { return "tarefa-" + id; }
}
