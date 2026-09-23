package br.com.poc.application.service;

import br.com.poc.application.exception.InvalidInputException;
import br.com.poc.application.model.scheduler.TarefaStatus;
import org.springframework.stereotype.Service;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

@Service
public class TarefaStatusTransitionService {

    private static final String ORIGIN = "TAREFA_STATUS_TRANSITION";

    private static final Map<TarefaStatus, Set<TarefaStatus>> ALLOWED_TRANSITIONS = Map.of(
        TarefaStatus.PRONTA, EnumSet.of(TarefaStatus.AGENDADA, TarefaStatus.DESABILITADA),
        TarefaStatus.AGENDADA, EnumSet.of(TarefaStatus.PRONTA, TarefaStatus.EXECUTANDO),
        TarefaStatus.EXECUTANDO, EnumSet.of(TarefaStatus.AGENDADA, TarefaStatus.FINALIZADA, TarefaStatus.ERRO, TarefaStatus.CANCELADA),
        TarefaStatus.FINALIZADA, EnumSet.of(TarefaStatus.PRONTA),
        TarefaStatus.ERRO, EnumSet.of(TarefaStatus.PRONTA),
        TarefaStatus.CANCELADA, EnumSet.of(TarefaStatus.PRONTA),
        TarefaStatus.DESABILITADA, EnumSet.of(TarefaStatus.PRONTA, TarefaStatus.REMOVIDA),
        TarefaStatus.REMOVIDA, EnumSet.noneOf(TarefaStatus.class)
    );

    public boolean canTransition(TarefaStatus from, TarefaStatus to) {
        if (from == null || to == null) {
            return false;
        }
        return ALLOWED_TRANSITIONS.getOrDefault(from, EnumSet.noneOf(TarefaStatus.class)).contains(to);
    }

    public TarefaStatus transition(TarefaStatus from, TarefaStatus to) {
        if (from == null) {
            if (to == TarefaStatus.PRONTA) {
                return TarefaStatus.PRONTA;
            }
            throw new InvalidInputException(ORIGIN, "transition",
                "Transicao invalida de estado inicial nulo para " + to.name());
        }

        if (!canTransition(from, to)) {
            throw new InvalidInputException(
                ORIGIN,
                "transition",
                "Transicao invalida de " + from.name() + " para " + to.name()
            );
        }

        return to;
    }
}
