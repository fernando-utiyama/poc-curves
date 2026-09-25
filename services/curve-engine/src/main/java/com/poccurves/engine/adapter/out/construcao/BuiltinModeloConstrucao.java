package com.poccurves.engine.adapter.out.construcao;
import com.poccurves.engine.application.construcao.CurvaJuros;
import com.poccurves.engine.application.construcao.Vertice;
import com.poccurves.engine.application.model.ModeloCurva;
import com.poccurves.engine.application.model.TipoModelo;
import com.poccurves.engine.application.port.ModeloConstrucaoPort;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Despacha para a implementação Java embutida (TipoModelo.BUILTIN) correspondente ao
 * {@code codigo} do modelo. Hoje só existe TAXA_SWAP_TRANSCRICAO_B3 — as curvas TS B3
 * (openspec/changes/b3-additional-curves) já vêm com o valor final calculado pela B3 (Manual de
 * Curvas), então a "construção" é a montagem direta de {@link CurvaJuros} a partir dos vértices
 * recebidos, sem nenhum cálculo — a estrutura fica pronta para outros modelos embutidos futuros
 * (ex. um bootstrap de verdade, se uma curva calibrada a partir de insumo bruto voltar a existir).
 */
@Component
public class BuiltinModeloConstrucao implements ModeloConstrucaoPort {

    /** Modelo embutido padrão das curvas TS B3 — ver {@link com.poccurves.engine.adapter.in.bootstrap.ModeloCurvaBootstrap}. */
    public static final String CODIGO_TAXA_SWAP_TRANSCRICAO_B3 = "TAXA_SWAP_TRANSCRICAO_B3";

    private final Map<String, Function<List<Vertice>, CurvaJuros>> implementacoesPorCodigo;

    public BuiltinModeloConstrucao() {
        this.implementacoesPorCodigo = Map.of(
                CODIGO_TAXA_SWAP_TRANSCRICAO_B3, CurvaJuros::de
        );
    }

    @Override
    public boolean suporta(ModeloCurva modelo) {
        return modelo.tipo() == TipoModelo.BUILTIN;
    }

    @Override
    public CurvaJuros construir(ModeloCurva modelo, List<Vertice> insumos) {
        Function<List<Vertice>, CurvaJuros> implementacao = implementacoesPorCodigo.get(modelo.codigo());
        if (implementacao == null) {
            throw new IllegalStateException("modelo embutido desconhecido: " + modelo.codigo());
        }
        return implementacao.apply(insumos);
    }
}
