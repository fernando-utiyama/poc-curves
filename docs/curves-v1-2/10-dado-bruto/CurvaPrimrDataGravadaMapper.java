package br.com.poc.adapter.out.persistence;

import br.com.poc.adapter.out.persistence.repository.CurvaPrimrDataGravadaProjection;
import br.com.poc.application.port.out.CurvaPrvdrRepositoryPort;
import br.com.poc.domain.SituacaoCurva;
import br.com.poc.domain.cadastro.CurvaPrimrDataGravada;
import br.com.poc.domain.cadastro.CurvaProvedor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * O que a listagem do dado bruto tem de igual nas três fontes (B3, ANBIMA e Bloomberg): os tickers do provedor
 * por curva e a conversão da linha agregada no resumo. Cada adaptador só informa o provedor e o produto.
 */
public final class CurvaPrimrDataGravadaMapper {

    private CurvaPrimrDataGravadaMapper() {}

    /** Nome da curva → tickers do provedor ligados a ela em tCurvaPrvdr (ex.: PRE na B3, NTN-B na ANBIMA). */
    public static Map<String, List<String>> tickersPorCurva(CurvaPrvdrRepositoryPort port, String provedor, String produto) {
        Map<String, List<String>> tickers = new HashMap<>();
        if (port != null) {
            for (CurvaProvedor cp : port.buscarCurvasProvedor(provedor, produto, null)) {
                tickers.computeIfAbsent(cp.nomeCurva(), k -> new ArrayList<>()).add(cp.tickerProvedor());
            }
        }
        return tickers;
    }

    public static List<CurvaPrimrDataGravada> paraDatasGravadas(List<CurvaPrimrDataGravadaProjection> linhas,
                                                      Map<String, List<String>> tickersPorCurva) {
        return linhas.stream()
            .map(p -> new CurvaPrimrDataGravada(
                p.getCodigo(),
                p.getNome(),
                situacaoOuNula(p.getSituacao()),
                p.getDataRef(),
                p.getQuantidade() != null ? p.getQuantidade() : 0L,
                tickersPorCurva.getOrDefault(p.getNome(), List.of()),
                Integer.valueOf(1).equals(p.getConstruida())))
            .toList();
    }

    /** Situação fora do enum vira nula, sem esconder qualquer outro erro. */
    static SituacaoCurva situacaoOuNula(String texto) {
        if (texto == null) {
            return null;
        }
        try {
            return SituacaoCurva.valueOf(texto.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
