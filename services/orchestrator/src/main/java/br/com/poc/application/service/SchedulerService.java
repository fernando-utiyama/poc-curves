package br.com.poc.application.service;

import br.com.poc.application.exception.InvalidInputException;
import br.com.poc.application.exception.NotFoundException;
import br.com.poc.application.model.scheduler.*;
import br.com.poc.application.port.in.SchedulerPort;
import br.com.poc.application.port.in.SchedulerUseCase;
import br.com.poc.application.port.out.TaskRepositoryPort;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Serviço que expõe operações de controle do scheduler (iniciar, parar e consultar status).
 *
 * Responsabilidades:
 * - Iniciar o agendamento de tarefas encontradas no repositório.
 * - Parar todos os agendamentos em execução.
 * - Expor um `SchedulerStatus` agregando tarefas atualmente em execução.
 */
@Service
@AllArgsConstructor
public class SchedulerService implements SchedulerUseCase {

    private static final String ORIGIN = "SCHEDULER";
    private static final String MSG_TAREFA_DESAGENDADA = "Tarefa desagendada com sucesso";
    private static final String MSG_TAREFA_CANCELADA = "Tarefa cancelada com sucesso";
    private static final String MSG_TAREFA_RESETADA = "Tarefa resetada para PRONTA";
    private static final String MSG_TAREFA_AGENDADA = "Tarefa agendada com sucesso";

    private final Map<Long, SchedulerTaskStatus> taskStatuses = new ConcurrentHashMap<>();
    private final Map<String, Boolean> runningTasks = new ConcurrentHashMap<>();

    private final TaskRepositoryPort taskRepositoryPort;
    private final SchedulerPort schedulerPort;
    private final TaskActionExecutor taskActionExecutor;
    private final TarefaStatusTransitionService transitionService;

    @Override
    public void startAll() {
        taskRepositoryPort.findPersistedScheduledTarefas().stream()
            .filter(this::possuiAgendamento)
            .forEach(this::startTaskInternally);
    }

    @Override
    public void stopAll() {
        schedulerPort.stopAll();
        runningTasks.clear();
    }

    @Override
    public SchedulerStatus status(String status, String nomeTarefa) {
        Set<Long> tarefaIds = buscarIdsPorNome(nomeTarefa);
        List<SchedulerTaskStatus> tasks = taskStatuses.values().stream()
            .filter(taskStatus -> correspondeAoStatus(taskStatus, status))
            .filter(taskStatus -> tarefaIds == null || tarefaIds.contains(taskStatus.getTarefaId()))
            .toList();

        return new SchedulerStatus(runningTasks, tasks);
    }

    private Set<Long> buscarIdsPorNome(String nomeTarefa) {
        if (nomeTarefa == null || nomeTarefa.isBlank()) {
            return null;
        }

        String nomeNormalizado = nomeTarefa.trim().toLowerCase(Locale.ROOT);
        return taskRepositoryPort.findAllTarefas().stream()
            .filter(tarefa -> tarefa.getNome() != null
                && tarefa.getNome().toLowerCase(Locale.ROOT).contains(nomeNormalizado))
            .map(Tarefa::getId)
            .collect(Collectors.toSet());
    }

    private boolean correspondeAoStatus(SchedulerTaskStatus taskStatus, String status) {
        return status == null || status.isBlank()
            || (taskStatus.getStatus() != null && taskStatus.getStatus().equalsIgnoreCase(status.trim()));
    }

    @Override
    @Transactional
    public void scheduleTask(Long tarefaId) {
        Tarefa tarefa = buscarPorId(tarefaId);
        validarAgendamento(tarefa);
        scheduleTaskInternally(tarefa);
    }

    @Override
    @Transactional
    public void stopTask(Long tarefaId) {
        Tarefa tarefa = buscarPorId(tarefaId);
        SchedulerTaskStatus statusAtual = getTaskStatus(tarefaId);
        TarefaStatus origem = TarefaStatus.fromValue(tarefa.getStatus());

        if (statusAtual != null && TarefaStatus.EXECUTANDO.name().equalsIgnoreCase(statusAtual.getStatus())) {
            throw new InvalidInputException(ORIGIN, "stopTask", "Nao e permitido desagendar tarefa em execucao");
        }

        if (!transitionService.canTransition(origem, TarefaStatus.PRONTA)) {
            throw new InvalidInputException(ORIGIN, "stopTask",
                "Transicao invalida de " + origem.name() + " para PRONTA");
        }

        schedulerPort.stop(schedulerKey(tarefa.getId()));
        runningTasks.remove(tarefa.getNome());

        aplicarTransicaoFinal(tarefa, origem, TarefaStatus.PRONTA, MSG_TAREFA_DESAGENDADA);
    }

    @Override
    @Transactional
    public void executeTask(Long tarefaId) {
        // Sem @Transactional: cada save commita por chamada, garantindo que o status final
        // (FINALIZADA ou ERRO) seja persistido mesmo quando a action lanca excecao.
        Tarefa tarefa = buscarPorId(tarefaId);
        prepararNovoCicloParaExecucaoManual(tarefa);
        executarTarefa(tarefa, true);
    }

    @Override
    @Transactional
    public void cancelTask(Long tarefaId) {
        Tarefa tarefa = buscarPorId(tarefaId);
        TarefaStatus origem = TarefaStatus.fromValue(tarefa.getStatus());

        if (!TarefaStatus.EXECUTANDO.equals(origem)) {
            throw new InvalidInputException(ORIGIN, "cancelTask",
                "Transicao invalida de " + origem.name() + " para CANCELADA");
        }

        schedulerPort.stop(schedulerKey(tarefa.getId()));
        runningTasks.remove(tarefa.getNome());

        aplicarTransicaoFinal(tarefa, origem, TarefaStatus.CANCELADA, MSG_TAREFA_CANCELADA);
    }

    @Override
    @Transactional
    public void resetTask(Long tarefaId) {
        Tarefa tarefa = buscarPorId(tarefaId);

        TarefaStatus origem = TarefaStatus.fromValue(tarefa.getStatus());
        if (origem == TarefaStatus.AGENDADA) {
            schedulerPort.stop(schedulerKey(tarefa.getId()));
            runningTasks.remove(tarefa.getNome());
        }

        aplicarTransicaoFinal(tarefa, origem, TarefaStatus.PRONTA, MSG_TAREFA_RESETADA);
    }

    @Override
    public SchedulerTaskStatus getTaskStatus(Long tarefaId) {
        Tarefa tarefa = buscarPorId(tarefaId);
        SchedulerTaskStatus statusMemoria = obterOuCriarStatus(tarefa.getId());
        statusMemoria.setTarefaId(tarefa.getId());

        if (statusMemoria.getStatus() == null) {
            statusMemoria.setStatus(tarefa.getStatus());
        }
        if (statusMemoria.getUltimaMensagem() == null) {
            statusMemoria.setUltimaMensagem(tarefa.getUltimaMensagem() != null
                ? tarefa.getUltimaMensagem()
                : obterUltimaMensagem(tarefa));
        }
        if (statusMemoria.getUltimaExecucao() == null) {
            statusMemoria.setUltimaExecucao(converterParaLocalDateTime(tarefa.getUltimaExecucao()));
        }
        if (statusMemoria.getProximaExecucao() == null) {
            statusMemoria.setProximaExecucao(converterParaLocalDateTime(tarefa.getProximaExecucao()));
        }

        return statusMemoria;
    }

    private void scheduleTaskInternally(Tarefa tarefa) {
        //modificado
        if (!possuiAgendamento(tarefa)) {
            throw new InvalidInputException(
                ORIGIN,
                "scheduleTask",
                "Tarefa nao possui regra de agendamento"
            );
        }

        TarefaStatus origem = TarefaStatus.fromValue(tarefa.getStatus());
        tarefa.setStatus(
            transitionService.transition(origem, TarefaStatus.AGENDADA).name()
        );

        registrarAgendamento(tarefa, true);
    }

    private void startTaskInternally(Tarefa tarefa) {
        if (!possuiAgendamento(tarefa)) {
            throw new InvalidInputException(
                ORIGIN,
                "startAll",
                "Tarefa nao possui regra de agendamento"
            );
        }

        TarefaStatus origem = TarefaStatus.fromValue(tarefa.getStatus());

        if (origem != TarefaStatus.AGENDADA) {
            scheduleTaskInternally(tarefa);
            return;
        }

        registrarAgendamento(tarefa, false);
    }

    private void registrarAgendamento(Tarefa tarefa, boolean comLog) {
        schedulerPort.schedule(tarefa, () -> executarTarefa(tarefa, false));
        runningTasks.put(tarefa.getNome(), true);

        SchedulerTaskStatus schedulerStatus =
            schedulerPort.getTaskStatus(schedulerKey(tarefa.getId()));

        tarefa.setDataAtualizacao(LocalDateTime.now());
        tarefa.setUltimaMensagem(MSG_TAREFA_AGENDADA);

        if (schedulerStatus != null
            && schedulerStatus.getProximaExecucao() != null) {
            tarefa.setProximaExecucao(
                converterParaInstant(schedulerStatus.getProximaExecucao())
            );
        }

        if (comLog) {
            adicionarLog(tarefa, 200, tarefa.getUltimaMensagem());
        }
        taskRepositoryPort.save(tarefa);

        SchedulerTaskStatus status = obterOuCriarStatus(tarefa.getId());
        status.setTarefaId(tarefa.getId());
        status.setStatus(TarefaStatus.AGENDADA.name());
        status.setTeveErro(false);
        status.setUltimaMensagem(tarefa.getUltimaMensagem());

        if (schedulerStatus != null) {
            status.setProximaExecucao(schedulerStatus.getProximaExecucao());
        }
    }

    private void executarTarefa(Tarefa tarefa, boolean manual) {
        TarefaStatus origem = TarefaStatus.fromValue(tarefa.getStatus());
        LocalDateTime inicio = LocalDateTime.now();
        tarefa.setStatus(
            transitionService.transition(
                origem,
                TarefaStatus.EXECUTANDO
            ).name()
        );
        tarefa.setDataAtualizacao(inicio);
        tarefa.setUltimaMensagem(
            manual
                ? "Execucao manual iniciada"
                : "Execucao automatica iniciada"
        );

        SchedulerTaskStatus status = obterOuCriarStatus(tarefa.getId());
        status.setTarefaId(tarefa.getId());
        status.setStatus(TarefaStatus.EXECUTANDO.name());
        status.setTeveErro(false);
        status.setUltimaMensagem(tarefa.getUltimaMensagem());

        adicionarLog(tarefa, 102, tarefa.getUltimaMensagem());
        taskRepositoryPort.save(tarefa);

        try {
            taskActionExecutor.execute(tarefa);

            LocalDateTime fim = LocalDateTime.now();

            TarefaStatus destino = definirDestinoExecucao(tarefa, manual);
            tarefa.setStatus(
                transitionService.transition(
                    TarefaStatus.EXECUTANDO,
                    destino
                ).name()
            );
            tarefa.setDataAtualizacao(fim);
            tarefa.setUltimaExecucao(converterParaInstant(fim));
            tarefa.setUltimaMensagem("Tarefa executada com sucesso");

            if (manual) {
                // execucao manual encerra o ciclo: remove o agendamento pendente para nao voltar a AGENDADA
                schedulerPort.stop(schedulerKey(tarefa.getId()));
                runningTasks.remove(tarefa.getNome());
                tarefa.setProximaExecucao(null);
            } else {
                atualizarProximaExecucao(tarefa);
            }

            adicionarLog(tarefa, 200, tarefa.getUltimaMensagem());
            taskRepositoryPort.save(tarefa);

            status.setStatus(destino.name());
            status.setTeveErro(false);
            status.setUltimaMensagem(tarefa.getUltimaMensagem());
            status.setUltimaExecucao(fim);
            status.setProximaExecucao(
                converterParaLocalDateTime(tarefa.getProximaExecucao())
            );
        } catch (RuntimeException ex) {
            LocalDateTime fim = LocalDateTime.now();

            tarefa.setStatus(
                transitionService.transition(
                    TarefaStatus.EXECUTANDO,
                    TarefaStatus.ERRO
                ).name()
            );
            tarefa.setDataAtualizacao(fim);
            tarefa.setUltimaExecucao(converterParaInstant(fim));
            tarefa.setUltimaMensagem(
                ex.getMessage() != null
                    ? ex.getMessage()
                    : "Erro durante a execucao da tarefa"
            );

            atualizarProximaExecucao(tarefa);

            adicionarLog(tarefa, 500, tarefa.getUltimaMensagem());
            taskRepositoryPort.save(tarefa);

            status.setStatus(TarefaStatus.ERRO.name());
            status.setTeveErro(true);
            status.setUltimaMensagem(tarefa.getUltimaMensagem());
            status.setUltimaExecucao(fim);
            status.setProximaExecucao(
                converterParaLocalDateTime(tarefa.getProximaExecucao())
            );

            throw ex;
        }
    }

    private void prepararNovoCicloParaExecucaoManual(Tarefa tarefa) {
        TarefaStatus statusAtual = TarefaStatus.fromValue(tarefa.getStatus());
        if (statusAtual != TarefaStatus.FINALIZADA
            && statusAtual != TarefaStatus.ERRO
            && statusAtual != TarefaStatus.CANCELADA) {
            return;
        }

        tarefa.setStatus(transitionService.transition(statusAtual, TarefaStatus.PRONTA).name());
        tarefa.setDataAtualizacao(LocalDateTime.now());
        tarefa.setUltimaMensagem("Tarefa preparada para nova execucao manual");
        tarefa.setProximaExecucao(null);
        adicionarLog(tarefa, 200, tarefa.getUltimaMensagem());
        taskRepositoryPort.save(tarefa);

        validarAgendamento(tarefa);
        scheduleTaskInternally(tarefa);
    }

    private void validarAgendamento(Tarefa tarefa) {
        if (tarefa == null) {
            throw new InvalidInputException(ORIGIN, "scheduleTask", "Tarefa nao informada");
        }
        if (TarefaStatus.DESABILITADA.name().equalsIgnoreCase(tarefa.getStatus())) {
            throw new InvalidInputException(ORIGIN, "scheduleTask", "Nao e permitido agendar tarefa desabilitada");
        }
        if (TarefaStatus.REMOVIDA.name().equalsIgnoreCase(tarefa.getStatus())) {
            throw new InvalidInputException(ORIGIN, "scheduleTask", "Nao e permitido agendar tarefa removida");
        }
    }

    private Tarefa buscarPorId(Long id) {
        Optional<Tarefa> tarefa = taskRepositoryPort.findTarefaById(id);
        return tarefa.orElseThrow(() ->
            new NotFoundException("TAREFA_NAO_ENCONTRADA", "Tarefa nao encontrada"));
    }

    private SchedulerTaskStatus obterOuCriarStatus(Long tarefaId) {
        return taskStatuses.computeIfAbsent(tarefaId, ignored -> new SchedulerTaskStatus());
    }

    private boolean possuiAgendamento(Tarefa tarefa) {
        return (tarefa.getRegraCron() != null && !tarefa.getRegraCron().isBlank())
            || (tarefa.getRegraIntervalo() != null && !tarefa.getRegraIntervalo().isBlank());
    }

    private boolean isRecorrente(Tarefa tarefa) {
        if (tarefa.getRegraCron() != null && !tarefa.getRegraCron().isBlank()) {
            return true;
        }
        String intervalo = tarefa.getRegraIntervalo();
        if (intervalo == null || intervalo.isBlank()) {
            return false;
        }
        try {
            Duration.parse(intervalo);
            return true;
        } catch (DateTimeParseException ex) {
            return false;
        }
    }

    private TarefaStatus definirDestinoExecucao(Tarefa tarefa, boolean manual) {
        if (manual) {
            return TarefaStatus.FINALIZADA;
        }
        return isRecorrente(tarefa) ? TarefaStatus.AGENDADA : TarefaStatus.FINALIZADA;
    }

    private void aplicarTransicaoFinal(Tarefa tarefa, TarefaStatus origem, TarefaStatus destino, String mensagem) {
        tarefa.setStatus(transitionService.transition(origem, destino).name());
        adicionarLog(tarefa, 200, mensagem);
        tarefa.setDataAtualizacao(LocalDateTime.now());
        tarefa.setUltimaMensagem(mensagem);

        adicionarLog(tarefa, 200, tarefa.getUltimaMensagem());
        taskRepositoryPort.save(tarefa);

        SchedulerTaskStatus status = obterOuCriarStatus(tarefa.getId());
        status.setTarefaId(tarefa.getId());
        status.setStatus(destino.name());
        status.setTeveErro(false);
        status.setUltimaMensagem(mensagem);
        status.setProximaExecucao(null);
    }

    private String schedulerKey(Long id) { return "tarefa-" + id; }

    private void adicionarLog(Tarefa tarefa, Integer codigo, String texto) {
        LogTarefa log = new LogTarefa();
        log.setCodigo(codigo);
        log.setTexto(texto);
        log.setDataCriacao(LocalDateTime.now());
        tarefa.getLogs().add(log);
    }

    private String obterUltimaMensagem(Tarefa tarefa) {
        if (tarefa.getLogs() == null || tarefa.getLogs().isEmpty()) {
            return null;
        }
        return tarefa.getLogs().get(tarefa.getLogs().size() - 1).getTexto();
    }

    private void atualizarProximaExecucao(Tarefa tarefa) {
        SchedulerTaskStatus schedulerStatus =
            schedulerPort.getTaskStatus(schedulerKey(tarefa.getId()));

        if (schedulerStatus == null
            || schedulerStatus.getProximaExecucao() == null) {
            tarefa.setProximaExecucao(null);
            return;
        }

        tarefa.setProximaExecucao(
            converterParaInstant(schedulerStatus.getProximaExecucao())
        );
    }

    private Instant converterParaInstant(LocalDateTime data) {
        if (data == null) {
            return null;
        }

        return data.atZone(ZoneId.systemDefault()).toInstant();
    }

    private LocalDateTime converterParaLocalDateTime(Instant data) {
        if (data == null) {
            return null;
        }

        return LocalDateTime.ofInstant(data, ZoneId.systemDefault());
    }
}
