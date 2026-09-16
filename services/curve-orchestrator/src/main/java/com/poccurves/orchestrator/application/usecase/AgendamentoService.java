package com.poccurves.orchestrator.application.usecase;
import com.poccurves.orchestrator.application.model.Agendamento;
import com.poccurves.orchestrator.application.model.AgendamentoComUltimaExecucao;
import com.poccurves.orchestrator.application.model.Faixa;
import com.poccurves.orchestrator.application.model.MomentoCurva;
import com.poccurves.orchestrator.application.port.AgendamentoRepositoryPort;


import java.util.List;
import java.util.UUID;

/**
 * Cadastro de agendamentos — só escreve no banco. A convergência do registro local de cada
 * réplica (registrar/cancelar/reagendar no {@code AgendamentoSchedulerPort}) não é mais
 * responsabilidade daqui: é feita pela reconciliação periódica ({@code ReconciliacaoAgendamentosService},
 * openspec/changes/orchestrator-multi-instance-scheduling) — antes, mutar o scheduler na mesma
 * requisição HTTP só convergia a réplica que a atendeu, deixando as demais desatualizadas até
 * reiniciarem.
 */
public class AgendamentoService {

    private final AgendamentoRepositoryPort agendamentoRepository;

    public AgendamentoService(AgendamentoRepositoryPort agendamentoRepository) {
        this.agendamentoRepository = agendamentoRepository;
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
        return agendamento;
    }

    public List<Agendamento> listar() {
        return agendamentoRepository.listarTodos();
    }

    public List<AgendamentoComUltimaExecucao> buscarComUltimaExecucao() {
        return agendamentoRepository.buscarComUltimaExecucao();
    }
}
