package com.poccurves.orchestrator.application;

import com.poccurves.orchestrator.domain.LinhaExecucaoResumo;
import com.poccurves.orchestrator.domain.PaginaExecucoes;
import com.poccurves.orchestrator.dto.OrchestratorDtos.ExecucoesResponse;
import com.poccurves.orchestrator.dto.OrchestratorDtos.ItemExecucaoDTO;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Serviço de consulta de execuções de curvas com filtros e paginação.
 * <p>
 * Implementa as tarefas 11.1 e 11.2 do backlog curve-orchestrator.
 */
public class ExecucoesService {

    private final ExecucaoCurvaRepositoryPort repository;

    public ExecucoesService(ExecucaoCurvaRepositoryPort repository) {
        this.repository = repository;
    }

    /**
     * Consulta execuções aplicando filtros opcionais e paginação.
     * <p>
     * Tarefas 11.1 e 11.2 do backlog curve-orchestrator.
     * <p>
     * Nota: O campo {@code etapaAtual} de {@link ItemExecucaoDTO} é sempre retornado como {@code null},
     * pois o rastreio de etapa (grupo 3 do backlog) ainda não foi implementado.
     *
     * @param codigoCurva código da curva ou conjunto de dados para filtro opcional
     * @param dataReferencia data de referência para filtro opcional
     * @param estado estado da execução para filtro opcional
     * @param pagina número da página (0-based)
     * @param tamanho quantidade de elementos por página
     * @return {@link ExecucoesResponse} contendo os itens mapeados, total de elementos e total de páginas
     */
    public ExecucoesResponse listarExecucoes(String codigoCurva, LocalDate dataReferencia, String estado, int pagina, int tamanho) {
        PaginaExecucoes paginaExecucoes = repository.buscarComFiltros(codigoCurva, dataReferencia, estado, pagina, tamanho);

        List<ItemExecucaoDTO> itens = paginaExecucoes.itens().stream()
                .map(this::mapearParaItemDto)
                .toList();

        int totalPaginas = tamanho > 0 ? (int) Math.ceil((double) paginaExecucoes.totalElementos() / tamanho) : 0;

        return new ExecucoesResponse(itens, paginaExecucoes.totalElementos(), pagina, totalPaginas);
    }

    private ItemExecucaoDTO mapearParaItemDto(LinhaExecucaoResumo linha) {
        Integer duracaoSegundos = null;
        if (linha.iniciadoEm() != null) {
            Instant fim = linha.finalizadoEm() != null ? linha.finalizadoEm() : Instant.now();
            duracaoSegundos = (int) Duration.between(linha.iniciadoEm(), fim).getSeconds();
        }

        return new ItemExecucaoDTO(
                linha.id(),
                linha.correlacaoId() != null ? linha.correlacaoId().toString() : null,
                linha.tipoDisparo(),
                linha.codigoCurva(),
                linha.dataReferencia(),
                linha.momentoCurva(),
                linha.estado(),
                null,
                duracaoSegundos,
                linha.iniciadoEm(),
                linha.finalizadoEm(),
                linha.disparadoPor(),
                linha.mensagemErro()
        );
    }
}
