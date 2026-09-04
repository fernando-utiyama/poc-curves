package com.poccurves.processor.application;

import com.poccurves.processor.domain.DivergenciaValor;
import com.poccurves.processor.domain.EstadoLoteIngestao;
import com.poccurves.processor.domain.LoteIngestao;
import com.poccurves.processor.domain.LoteJaExisteException;
import com.poccurves.processor.domain.PontoDadoMercado;
import com.poccurves.processor.domain.ResultadoProcessamentoBloco;
import com.poccurves.processor.domain.TipoPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Orquestra a persistência de um bloco recebido: abre ou recarrega o lote,
 * grava os pontos em ordem determinística (task 4.6, contra deadlock entre
 * faixas), detecta divergências, consolida o lote. Tudo numa única
 * transação por bloco (task 4.2) — se qualquer upsert falhar, o bloco
 * inteiro é revertido e o lote permanece como estava antes desta chamada.
 */
public class IngestaoService {

    private static final Logger log = LoggerFactory.getLogger(IngestaoService.class);

    private final LoteIngestaoRepositoryPort loteRepository;
    private final PontoDadoMercadoRepositoryPort pontoRepository;
    private final ExecucaoCurvaLeituraRepositoryPort execucaoCurvaLeituraRepository;

    public IngestaoService(
            LoteIngestaoRepositoryPort loteRepository,
            PontoDadoMercadoRepositoryPort pontoRepository,
            ExecucaoCurvaLeituraRepositoryPort execucaoCurvaLeituraRepository) {
        this.loteRepository = loteRepository;
        this.pontoRepository = pontoRepository;
        this.execucaoCurvaLeituraRepository = execucaoCurvaLeituraRepository;
    }

    /**
     * Processa um bloco: se {@code eventId} já é o último bloco registrado
     * neste lote, é uma redelivery do mesmo bloco — no-op idempotente
     * (retorna o estado atual sem regravar nada). Caso contrário, abre o
     * lote (primeiro bloco) ou recarrega o existente, grava os pontos e
     * consolida.
     *
     * @throws IllegalStateException se o lote já não estiver ABERTO e o
     *                                 bloco não for uma redelivery do último processado
     */
    @Transactional
    public ResultadoProcessamentoBloco processarBloco(
            String fonte,
            String dataset,
            LocalDate dataReferenciaLote,
            String loteExternoId,
            java.util.UUID correlationId,
            String eventId,
            String hashPayload,
            int sequencia,
            int totalBlocos,
            TipoPayload tipoPayload,
            List<PontoDadoMercado> pontos
    ) {
        Optional<LoteIngestao> existente = loteRepository.buscarPorLoteExternoId(loteExternoId);

        if (existente.isPresent() && eventId.equals(existente.get().idEvento())) {
            log.info("bloco já processado (eventId repetido), ignorando: loteExternoId={} eventId={}", loteExternoId, eventId);
            return new ResultadoProcessamentoBloco(existente.get(), List.of());
        }

        LoteIngestao lote;
        if (existente.isPresent()) {
            lote = existente.get();
            if (lote.estado() != EstadoLoteIngestao.ABERTO) {
                log.warn("bloco recebido para lote que não está mais ABERTO (estado={}), ignorando: loteExternoId={} eventId={}",
                        lote.estado(), loteExternoId, eventId);
                return new ResultadoProcessamentoBloco(lote, List.of());
            }
        } else {
            // Resolve a execução de curva que disparou esta aquisição pelo correlationId
            // propagado pelo curve-orchestrator (AquisicaoExecutionService usa
            // execucao_curva.correlacao_id como correlationId do feeder) — se não houver
            // nenhuma (ex.: pull ad-hoc fora do orchestrator), fica null, e é isso mesmo:
            // a FK em lote_ingestao é opcional, ver LoteIngestao/db/migration/V2.
            java.util.UUID execucaoCurvaId = execucaoCurvaLeituraRepository.buscarPorCorrelacaoId(correlationId)
                    .map(ExecucaoCurvaLeituraRepositoryPort.ExecucaoCurvaResumo::id)
                    .orElse(null);

            // Insere já para obter o id gerado pelo banco — a FK de ponto_dado_mercado
            // para lote_ingestao exige um id real antes de gravar qualquer ponto deste bloco.
            lote = LoteIngestao.abrir(execucaoCurvaId, fonte, dataset, tipoPayload, dataReferenciaLote, loteExternoId, correlationId, eventId, hashPayload, totalBlocos);
            try {
                loteRepository.inserir(lote);
            } catch (LoteJaExisteException corridaPerdida) {
                // Duas faixas (tópicos/consumer groups distintos) processando o mesmo
                // loteExternoId concorrentemente: ambas viram "não existe" na leitura acima
                // e tentam abrir o lote; a restrição UNIQUE (V11) barra a segunda, que
                // recarrega o lote vencedor em vez de duplicar ou propagar o erro (tarefa 8.19).
                LoteIngestao loteVencedor = loteRepository.buscarPorLoteExternoId(loteExternoId)
                        .orElseThrow(() -> corridaPerdida);
                if (eventId.equals(loteVencedor.idEvento())) {
                    log.info("corrida entre faixas para o mesmo lote, eventId já processado pelo vencedor: loteExternoId={} eventId={}",
                            loteExternoId, eventId);
                    return new ResultadoProcessamentoBloco(loteVencedor, List.of());
                }
                if (loteVencedor.estado() != EstadoLoteIngestao.ABERTO) {
                    log.warn("corrida entre faixas para o mesmo lote, vencedor já não está ABERTO (estado={}): loteExternoId={} eventId={}",
                            loteVencedor.estado(), loteExternoId, eventId);
                    return new ResultadoProcessamentoBloco(loteVencedor, List.of());
                }
                lote = loteVencedor;
            }
        }

        List<PontoDadoMercado> pontosOrdenados = new ArrayList<>(pontos);
        pontosOrdenados.sort(Comparator.comparing(PontoDadoMercado::chaveInstrumento));

        List<DivergenciaValor> divergencias = new ArrayList<>();
        for (PontoDadoMercado ponto : pontosOrdenados) {
            pontoRepository.upsert(ponto, lote.id())
                    .ifPresent(divergencias::add);
        }

        lote.registrarBloco(eventId, pontos.size(), pontos.size());
        lote.somarDivergencias(divergencias.size());
        loteRepository.atualizar(lote);

        return new ResultadoProcessamentoBloco(lote, divergencias);
    }
}
