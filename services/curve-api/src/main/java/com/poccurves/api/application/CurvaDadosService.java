package com.poccurves.api.application;

import com.poccurves.api.domain.PontoCurva;
import com.poccurves.api.dto.ApiDtos.ComparacaoCurvasRequest;
import com.poccurves.api.dto.ApiDtos.ComparacaoCurvasResponse;
import com.poccurves.api.dto.ApiDtos.CurvaDadosResponse;
import com.poccurves.api.dto.ApiDtos.ItemComparacaoCurvasDTO;
import com.poccurves.api.dto.ApiDtos.PontoCurvaDTO;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

public class CurvaDadosService {

    private final CurvaDadosRepositoryPort repository;

    public CurvaDadosService(CurvaDadosRepositoryPort repository) {
        this.repository = repository;
    }

    public CurvaDadosResponse consultarVertices(String ticker, LocalDate dataReferencia) {
        List<PontoCurva> pontos = repository.buscarVertices(ticker, dataReferencia);
        exigirNaoVazio(pontos, ticker, dataReferencia);
        return paraResponse(ticker, dataReferencia, pontos);
    }

    public CurvaDadosResponse consultarCurvaConstruida(String ticker, LocalDate dataReferencia) {
        List<PontoCurva> pontos = repository.buscarCurvaConstruida(ticker, dataReferencia);
        exigirNaoVazio(pontos, ticker, dataReferencia);
        return paraResponse(ticker, dataReferencia, pontos);
    }

    /**
     * Compara duas curvas construídas ({@code tCurvaData}) vértice a vértice, sem interpolar —
     * mesmo espírito do antigo ComparadorCurvas, mas com a chave sendo a data do vértice (não
     * mais prazoDiasUteis, conceito que não existe no schema novo).
     */
    public ComparacaoCurvasResponse compararCurvas(ComparacaoCurvasRequest request) {
        String tickerA = request.tickerA();
        String tickerB = request.tickerB();
        LocalDate dataReferencia = request.dataReferencia();

        List<PontoCurva> pontosA = repository.buscarCurvaConstruida(tickerA, dataReferencia);
        List<PontoCurva> pontosB = repository.buscarCurvaConstruida(tickerB, dataReferencia);

        if (pontosA.isEmpty() && pontosB.isEmpty()) {
            throw new NoSuchElementException(
                    "Nenhuma curva construída encontrada para '" + tickerA + "' nem '" + tickerB + "' em " + dataReferencia);
        }

        Map<LocalDate, BigDecimal> valoresA = paraMapa(pontosA);
        Map<LocalDate, BigDecimal> valoresB = paraMapa(pontosB);

        TreeSet<LocalDate> todasAsDatas = new TreeSet<>();
        todasAsDatas.addAll(valoresA.keySet());
        todasAsDatas.addAll(valoresB.keySet());

        List<ItemComparacaoCurvasDTO> itens = todasAsDatas.stream()
                .map(data -> new ItemComparacaoCurvasDTO(data, valoresA.get(data), valoresB.get(data)))
                .toList();

        return new ComparacaoCurvasResponse(dataReferencia, tickerA, tickerB, itens);
    }

    private void exigirNaoVazio(List<PontoCurva> pontos, String ticker, LocalDate dataReferencia) {
        if (pontos.isEmpty()) {
            throw new NoSuchElementException(
                    "Nenhum vértice encontrado para o ticker '" + ticker + "' em " + dataReferencia);
        }
    }

    private CurvaDadosResponse paraResponse(String ticker, LocalDate dataReferencia, List<PontoCurva> pontos) {
        List<PontoCurvaDTO> itens = pontos.stream()
                .map(p -> new PontoCurvaDTO(p.dataVertice(), p.valor()))
                .toList();
        return new CurvaDadosResponse(ticker, dataReferencia, itens);
    }

    private Map<LocalDate, BigDecimal> paraMapa(List<PontoCurva> pontos) {
        return pontos.stream()
                .collect(Collectors.toMap(PontoCurva::dataVertice, PontoCurva::valor, (a, b) -> a, TreeMap::new));
    }
}
