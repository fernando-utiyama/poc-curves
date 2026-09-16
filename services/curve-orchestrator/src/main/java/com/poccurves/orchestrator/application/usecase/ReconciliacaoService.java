package com.poccurves.orchestrator.application.usecase;
import com.poccurves.orchestrator.application.model.EstadoExecucao;
import com.poccurves.orchestrator.application.model.ExecucaoCurva;
import com.poccurves.orchestrator.application.port.ExecucaoCurvaRepositoryPort;


import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Reconcilia execuções deixadas em estado não-terminal por tempo maior que
 * {@code limiteAntiguidade} — chamado tanto na subida da aplicação (reinicialização abrupta do
 * processo anterior) quanto periodicamente (openspec/changes/orchestrator-multi-instance-scheduling):
 * com múltiplas réplicas, uma execução presa por uma réplica que caiu no meio do processamento só
 * é recuperada se outra réplica viva detectar isso — esperar por um reinício não é mais
 * suficiente. O filtro por tempo evita derrubar execução legítima de uma réplica saudável ainda
 * dentro da janela de tentativa.
 */
public class ReconciliacaoService {

    private final ExecucaoCurvaRepositoryPort execucaoCurvaRepository;

    public ReconciliacaoService(ExecucaoCurvaRepositoryPort execucaoCurvaRepository) {
        this.execucaoCurvaRepository = execucaoCurvaRepository;
    }

    /** @return quantidade de execuções presas reconciliadas (marcadas como falhas) */
    public int reconciliarExecucoesPresas(Duration limiteAntiguidade) {
        Instant iniciadoAntesDe = Instant.now().minus(limiteAntiguidade);
        List<ExecucaoCurva> presas = execucaoCurvaRepository.buscarExecucoesEmAndamento(iniciadoAntesDe);

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
