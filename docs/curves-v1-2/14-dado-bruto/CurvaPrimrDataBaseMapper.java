package br.com.poc.adapter.out.persistence;

import br.com.poc.adapter.out.persistence.repository.CurvaPrimrDataBaseProjection;
import br.com.poc.application.port.out.CurvaPrvdrRepositoryPort;
import br.com.poc.domain.SituacaoCurva;
import br.com.poc.domain.cadastro.CurvaPrimrDataBase;
import br.com.poc.domain.cadastro.CurvaProvedor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Partes comuns da listagem do dado bruto. */
public final class CurvaPrimrDataBaseMapper {

    private CurvaPrimrDataBaseMapper() {}

    /** Nome da curva → tickers do provedor. */
    public static Map<String, List<String>> tickersPorCurva(CurvaPrvdrRepositoryPort port, String provedor, String produto) {
        Map<String, List<String>> tickers = new HashMap<>();
        if (port != null) {
            for (CurvaProvedor cp : port.buscarCurvasProvedor(provedor, produto, null)) {
                tickers.computeIfAbsent(cp.nomeCurva(), k -> new ArrayList<>()).add(cp.tickerProvedor());
            }
        }
        return tickers;
    }

    public static List<CurvaPrimrDataBase> paraDatasBase(List<CurvaPrimrDataBaseProjection> linhas,
                                                      Map<String, List<String>> tickersPorCurva) {
        return linhas.stream()
            .map(p -> new CurvaPrimrDataBase(
                p.getCodigo(),
                p.getNome(),
                situacaoOuNula(p.getSituacao()),
                p.getDataRef(),
                p.getQuantidade() != null ? p.getQuantidade() : 0L,
                tickersPorCurva.getOrDefault(p.getNome(), List.of()),
                Integer.valueOf(1).equals(p.getConstruida())))
            .toList();
    }

    /** Situação fora do enum vira nula. */
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
