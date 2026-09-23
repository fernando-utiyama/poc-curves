package br.com.poc.application.service;

import br.com.poc.application.exception.InvalidInputException;
import br.com.poc.application.exception.NotFoundException;
import br.com.poc.application.model.scheduler.LogTarefa;
import br.com.poc.application.model.scheduler.Tarefa;
import br.com.poc.application.model.scheduler.TarefaStatus;
import br.com.poc.application.port.in.SchedulerPort;
import br.com.poc.application.port.in.TarefaUseCase;
import br.com.poc.application.port.out.TaskRepositoryPort;
import br.com.poc.application.port.out.WebhookNotifierPort;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Serviço principal que implementa as operações de domínio para gerenciamento de tarefas.
 *
 * Responsabilidades principais:
 * - Criar/atualizar/deletar tarefas persistindo via `TaskRepositoryPort`.
 * - Agendar tarefas no `SchedulerPort` e manter mapa local de assinaturas para
 *   sincronização com o banco de dados.
 * - Executar ações associadas a uma tarefa via `TaskActionExecutor` e registrar logs
 *   de execução (sucesso/erro), além de notificar webhooks via `WebhookNotifierPort`.
 * - Reconciliar agendamentos persistidos após startup e periodicamente.
 *
 * Observações:
 * - Métodos públicos representam casos de uso (use case) e aplicam validações de entrada.
 * - Lógica de execução captura exceções para marcar o status da tarefa e propagar o erro
 *   quando apropriado.
 */
@Service
@RequiredArgsConstructor
public class TarefaService implements TarefaUseCase {

    private static final Logger LOGGER = LoggerFactory.getLogger(TarefaService.class);
    private static final String ORIGIN = "TAREFA";

    private final TaskRepositoryPort taskRepositoryPort;
    private final SchedulerPort schedulerPort;
    private final WebhookNotifierPort webhookNotifierPort;
    private final TaskActionExecutor taskActionExecutor;
    @Value("${scheduler.enabled:false}")
    private boolean schedulerEnabled;
    private final Map<Long, String> agendamentosSincronizados = new ConcurrentHashMap<>();
    private final Map<Long, Object> execucoesEmAndamento = new ConcurrentHashMap<>();

    @Override
    @Transactional
    public Tarefa criarTarefa(Tarefa tarefa) {
        validarTarefa(tarefa);

        LocalDateTime agora = LocalDateTime.now();
        tarefa.setDataCriacao(agora);
        tarefa.setDataAtualizacao(agora);
        tarefa.setUltimaMensagem("Tarefa agendada com sucesso");

        tarefa.setStatus(TarefaStatus.AGENDADA.name());
        adicionarLog(tarefa, 201, "Tarefa agendada com sucesso");

        Tarefa salva = taskRepositoryPort.save(tarefa);
        agendar(salva);
        webhookNotifierPort.notifyScheduleChanged("CRIADA", salva);
        LOGGER.info("Tarefa {} criada e agendada", salva.getId());
        return salva;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Tarefa> listarTarefas() {
        LOGGER.info("Listando tarefas agendadas");
        return taskRepositoryPort.findAllTarefas();
    }

    @Override
    @Transactional(readOnly = true)
    public Tarefa buscarTarefa(Long id) { return buscarPorId(id); }

    @Override
    @Transactional
    public Tarefa atualizarTarefa(Long id, Tarefa tarefa) {
        validarTarefa(tarefa);
        Tarefa atual = buscarPorId(id);

        atual.setNome(tarefa.getNome());
        atual.setDescricao(tarefa.getDescricao());
        atual.setAction(tarefa.getAction());
        atual.setRegraCron(tarefa.getRegraCron());
        atual.setRegraIntervalo(tarefa.getRegraIntervalo());
        atual.setStatus(TarefaStatus.AGENDADA.name());
        atual.setParametros(tarefa.getParametros());
        atual.setDataAtualizacao(LocalDateTime.now());
        atual.setUltimaMensagem("Tarefa atualizada e reagendada com sucesso");

        adicionarLog(atual, 200, "Tarefa atualizada e reagendada com sucesso");

        schedulerPort.stop(schedulerKey(id));
        Tarefa salva = taskRepositoryPort.save(atual);
        agendar(salva);
        webhookNotifierPort.notifyScheduleChanged("ATUALIZADA", salva);
        LOGGER.info("Tarefa {} atualizada e reagendada", salva.getId());
        return salva;
    }

    @Override
    @Transactional
    public void deletarTarefa(Long id) {
        Tarefa atual = buscarPorId(id);
        schedulerPort.stop(schedulerKey(id));
        webhookNotifierPort.notifyScheduleChanged("DELETADA", atual);
        taskRepositoryPort.deleteTarefaById(id);
        LOGGER.info("Tarefa {} deletada", id);
    }

    @Override
    @Transactional
    public Tarefa executarTarefa(Long id) { return executarComLock(id, true); }

    @Override
    @Transactional
    public Tarefa executarTarefa(String nome) {
        Tarefa tarefa = buscarPorNome(nome);
        return executarComLock(tarefa.getId(), true);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void agendarTarefasPersistidas() { sincronizarAgendamentosPersistidos(); }

    @Scheduled(fixedDelayString = "${scheduler.reconcile.fixed-delay:60000}")
    public void sincronizarAgendamentosPersistidos() {
        if (!schedulerEnabled) {
            LOGGER.debug("Reconciliação periódica do scheduler está desabilitada");
            return;
        }

        List<Tarefa> tarefasPersistidasAgendadas = taskRepositoryPort.findPersistedScheduledTarefas();
        Set<Long> idsPersistidosAgendados = new HashSet<>();

        for (Tarefa tarefa : tarefasPersistidasAgendadas) {
            if (tarefa.getId() == null || !possuiRegraDeAgendamento(tarefa)) {
                continue;
            }

            idsPersistidosAgendados.add(tarefa.getId());
            String assinatura = assinaturaAgendamento(tarefa);
            String assinaturaAtual = agendamentosSincronizados.get(tarefa.getId());

            if (!assinatura.equals(assinaturaAtual)) {
                agendar(tarefa);
                LOGGER.info("Tarefa {} sincronizada a partir do banco de dados", tarefa.getId());
            }
        }

        agendamentosSincronizados.keySet().removeIf(id -> {
            if (!idsPersistidosAgendados.contains(id)) {
                schedulerPort.stop(schedulerKey(id));
                LOGGER.info("Tarefa {} removida do agendamento local por alteracao no banco de dados", id);
                return true;
            }
            return false;
        });
    }

    @Transactional
    protected Tarefa executarTarefaAgendada(Long id) { return executarComLock(id, false); }

    private Tarefa executarComLock(Long id, boolean execucaoManual) {
        Object lock = execucoesEmAndamento.computeIfAbsent(id, ignored -> new Object());
        synchronized (lock) {
            try {
                return executar(buscarPorId(id), execucaoManual);
            } finally {
                execucoesEmAndamento.remove(id, lock);
            }
        }
    }

    private Tarefa executar(Tarefa tarefa, boolean execucaoManual) {
        tarefa.setStatus(TarefaStatus.EXECUTANDO.name());
        tarefa.setDataAtualizacao(LocalDateTime.now());
        tarefa.setUltimaExecucao(java.time.Instant.now());
        tarefa.setUltimaMensagem(execucaoManual ? "Execucao manual iniciada" : "Execucao automatica iniciada");

        adicionarLog(tarefa, 102, execucaoManual ? "Execucao manual iniciada" : "Execucao automatica iniciada");
        taskRepositoryPort.save(tarefa);

        try {
            taskActionExecutor.execute(tarefa);
            tarefa.setStatus(possuiRegraDeAgendamento(tarefa)
                ? TarefaStatus.AGENDADA.name()
                : TarefaStatus.FINALIZADA.name());
            tarefa.setDataAtualizacao(LocalDateTime.now());
            tarefa.setUltimaMensagem(execucaoManual ? "Execucao manual concluida" : "Execucao automatica concluida");

            adicionarLog(tarefa, 200, execucaoManual ? "Execucao manual concluida" : "Execucao automatica concluida");
            Tarefa salva = taskRepositoryPort.save(tarefa);
            webhookNotifierPort.notifyScheduleChanged("EXECUTADA", salva);
            return salva;
        } catch (RuntimeException ex) {
            tarefa.setStatus(TarefaStatus.ERRO.name());
            tarefa.setDataAtualizacao(LocalDateTime.now());
            tarefa.setUltimaMensagem(
                (execucaoManual ? "Falha na execucao manual: " : "Falha na execucao automatica: ") + ex.getMessage()
            );

            adicionarLog(tarefa, 500,
                (execucaoManual ? "Falha na execucao manual: " : "Falha na execucao automatica: ") + ex.getMessage());
            taskRepositoryPort.save(tarefa);
            webhookNotifierPort.notifyScheduleChanged("ERRO", tarefa);
            throw ex;
        }
    }

    private void agendar(Tarefa tarefa) {
        schedulerPort.schedule(tarefa, () -> executarTarefaAgendada(tarefa.getId()));
        agendamentosSincronizados.put(tarefa.getId(), assinaturaAgendamento(tarefa));
    }

    private Tarefa buscarPorId(Long id) {
        return taskRepositoryPort.findTarefaById(id)
            .orElseThrow(() -> new NotFoundException("TAREFA_NAO_ENCONTRADA", "Tarefa nao encontrada"));
    }

    private Tarefa buscarPorNome(String nome) {
        return taskRepositoryPort.findTarefaByNome(nome)
            .orElseThrow(() -> new NotFoundException("TAREFA_NAO_ENCONTRADA", "Tarefa nao encontrada"));
    }

    private void validarTarefa(Tarefa tarefa) {
        if (tarefa == null) {
            throw new InvalidInputException(ORIGIN, "validarTarefa", "Tarefa nao informada");
        }
        if (isBlank(tarefa.getNome()) || isBlank(tarefa.getDescricao()) || isBlank(tarefa.getAction())) {
            throw new InvalidInputException(ORIGIN, "validarTarefa", "Nome, descricao e action sao obrigatorios");
        }
        if (isBlank(tarefa.getRegraCron()) && isBlank(tarefa.getRegraIntervalo())) {
            throw new InvalidInputException(ORIGIN, "validarTarefa", "Informe regraCron ou regraIntervalo com data/hora ISO-8601");
        }
        if (!taskActionExecutor.supports(tarefa.getAction())) {
            throw new InvalidInputException(ORIGIN, "validarTarefa", "Action nao suportada: " + tarefa.getAction());
        }
    }

    private void adicionarLog(Tarefa tarefa, Integer codigo, String texto) {
        LogTarefa log = new LogTarefa();
        log.setCodigo(codigo);
        log.setTexto(texto);
        log.setDataCriacao(LocalDateTime.now());
        tarefa.getLogs().add(log);
    }

    private boolean isBlank(String value) { return value == null || value.isBlank(); }

    private boolean possuiRegraDeAgendamento(Tarefa tarefa) {
        return possuiRegraCron(tarefa) || !isBlank(tarefa.getRegraIntervalo());
    }

    private boolean possuiRegraCron(Tarefa tarefa) { return !isBlank(tarefa.getRegraCron()); }

    private String assinaturaAgendamento(Tarefa tarefa) {
        return String.join("|",
            valueOrBlank(tarefa.getRegraCron()),
            valueOrBlank(tarefa.getRegraIntervalo()),
            valueOrBlank(tarefa.getStatus()));
    }

    private String valueOrBlank(String value) { return value == null ? "" : value; }

    private String schedulerKey(Long id) { return "tarefa-" + id; }
}
