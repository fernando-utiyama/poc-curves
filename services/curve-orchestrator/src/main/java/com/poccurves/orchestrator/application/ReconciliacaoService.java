package com.poccurves.orchestrator.application;

import com.poccurves.orchestrator.domain.EstadoExecucao;
import com.poccurves.orchestrator.domain.ExecucaoCurva;

import java.util.List;

/**
 * Reconcilia execuções deixadas em estado não-terminal por uma reinicialização abrupta do
 * processo anterior — disparado uma vez na subida da aplicação.
 */
public class ReconciliacaoService {

    private final ExecucaoCurvaRepositoryPort execucaoCurvaRepository;

    public ReconciliacaoService(ExecucaoCurvaRepositoryPort execucaoCurvaRepository) {
        this.execucaoCurvaRepository = execucaoCurvaRepository;
    }

    /** @return quantidade de execuções presas reconciliadas (marcadas como falhas) */
    public int reconciliarExecucoesPresas() {
        List<ExecucaoCurva> presas = execucaoCurvaRepository.buscarExecucoesEmAndamento();

        for (ExecucaoCurva execucao : presas) {
            if (execucao.estado() == EstadoExecucao.PENDENTE) {
                execucao.iniciarExecucao();
            }
            execucao.falhar("SERVICO_REINICIADO", "Execução encontrada em estado não-terminal na inicialização do serviço — o processo anterior foi encerrado antes de concluir.");
            execucaoCurvaRepository.atualizar(execucao);
        }

        return presas.size();
    }
}
