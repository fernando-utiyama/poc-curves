package com.poccurves.orchestrator.application;

import com.poccurves.orchestrator.domain.Agendamento;
import com.poccurves.orchestrator.domain.AgendamentoComUltimaExecucao;
import com.poccurves.orchestrator.domain.Faixa;
import com.poccurves.orchestrator.domain.MomentoCurva;

import java.util.List;
import java.util.UUID;

public class AgendamentoService {

    private final AgendamentoRepositoryPort agendamentoRepository;
    private final AgendamentoSchedulerPort schedulerRegistry;

    public AgendamentoService(
            AgendamentoRepositoryPort agendamentoRepository,
            AgendamentoSchedulerPort schedulerRegistry
    ) {
        this.agendamentoRepository = agendamentoRepository;
        this.schedulerRegistry = schedulerRegistry;
    }

    public Agendamento cadastrar(
            UUID definicaoCurvaId,
            String conjuntoDados,
            MomentoCurva momentoCurva,
            Faixa faixa,
            String expressaoHorario,
            String fusoHorario,
            int janelaTentativaMinutos,
            int intervaloTentativaSegundos,
            String criadoPor
    ) {
        Agendamento agendamento = Agendamento.criar(
                definicaoCurvaId, conjuntoDados, momentoCurva, faixa,
                expressaoHorario, fusoHorario, janelaTentativaMinutos,
                intervaloTentativaSegundos, criadoPor
        );
        agendamentoRepository.inserir(agendamento);
        schedulerRegistry.registrar(agendamento);
        return agendamento;
    }

    public Agendamento editar(
            UUID id,
            String expressaoHorario,
            String fusoHorario,
            int janelaTentativaMinutos,
            int intervaloTentativaSegundos,
            Faixa faixa
    ) {
        Agendamento agendamento = agendamentoRepository.buscarPorId(id)
                .orElseThrow(() -> new IllegalArgumentException("Agendamento não encontrado com o ID " + id));

        agendamento.editar(expressaoHorario, fusoHorario, janelaTentativaMinutos, intervaloTentativaSegundos, faixa);
        agendamentoRepository.atualizar(agendamento);

        if (agendamento.ativo()) {
            schedulerRegistry.reagendar(agendamento);
        }
        return agendamento;
    }

    public Agendamento ativar(UUID id) {
        Agendamento agendamento = agendamentoRepository.buscarPorId(id)
                .orElseThrow(() -> new IllegalArgumentException("Agendamento não encontrado com o ID " + id));

        if (agendamento.ativo()) {
            return agendamento;
        }

        agendamento.ativar();
        agendamentoRepository.atualizar(agendamento);
        schedulerRegistry.registrar(agendamento);
        return agendamento;
    }

    public Agendamento desativar(UUID id) {
        Agendamento agendamento = agendamentoRepository.buscarPorId(id)
                .orElseThrow(() -> new IllegalArgumentException("Agendamento não encontrado com o ID " + id));

        if (!agendamento.ativo()) {
            return agendamento;
        }

        agendamento.desativar();
        agendamentoRepository.atualizar(agendamento);
        schedulerRegistry.cancelar(id);
        return agendamento;
    }

    public List<Agendamento> listar() {
        return agendamentoRepository.listarTodos();
    }

    public List<AgendamentoComUltimaExecucao> buscarComUltimaExecucao() {
        return agendamentoRepository.buscarComUltimaExecucao();
    }
}
