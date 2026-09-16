package com.poccurves.orchestrator.application.port;
import com.poccurves.orchestrator.application.model.ExecucaoCurva;
import com.poccurves.orchestrator.application.model.MomentoCurva;
import com.poccurves.orchestrator.application.model.PaginaExecucoes;


import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExecucaoCurvaRepositoryPort {
    Optional<ExecucaoCurva> buscarPorId(UUID id);
    void inserir(ExecucaoCurva execucao);
    void atualizar(ExecucaoCurva execucao);
    boolean existeExecucaoAtivaParaConjuntoDados(String conjuntoDados, LocalDate dataReferencia, MomentoCurva momentoCurva);
    boolean existeExecucaoAtivaParaDefinicaoCurva(UUID definicaoCurvaId, LocalDate dataReferencia, MomentoCurva momentoCurva);
    Optional<ExecucaoCurva> buscarExecucaoAtivaParaConjuntoDados(String conjuntoDados, LocalDate dataReferencia, MomentoCurva momentoCurva);
    PaginaExecucoes buscarComFiltros(String codigoCurva, LocalDate dataReferencia, String estado, int pagina, int tamanho);

    /**
     * Execuções não-terminais iniciadas antes de {@code iniciadoAntesDe} — o filtro por tempo
     * existe para que a reconciliação periódica (openspec/changes/orchestrator-multi-instance-scheduling)
     * não derrube uma execução legítima de outra réplica ainda dentro da janela de tentativa.
     */
    List<ExecucaoCurva> buscarExecucoesEmAndamento(Instant iniciadoAntesDe);
}
