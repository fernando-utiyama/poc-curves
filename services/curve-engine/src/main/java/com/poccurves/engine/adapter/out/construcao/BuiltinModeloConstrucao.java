package com.poccurves.engine.adapter.out.construcao;
import com.poccurves.engine.domain.construcao.CurveBootstrapper;
import com.poccurves.engine.domain.construcao.InsumoDI1;
import com.poccurves.engine.domain.construcao.ModeloCurva;
import com.poccurves.engine.domain.construcao.TipoModelo;
import com.poccurves.engine.domain.curva.CurvaJuros;

import com.poccurves.engine.application.ModeloConstrucaoPort;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Despacha para a implementação Java embutida (TipoModelo.BUILTIN) correspondente ao
 * {@code codigo} do modelo. Hoje só existe uma: PRE_DI1_B3 (montagem direta a partir de DI1,
 * {@link CurveBootstrapper}) — a estrutura fica pronta para outros modelos embutidos futuros.
 */
@Component
public class BuiltinModeloConstrucao implements ModeloConstrucaoPort {

    private final Map<String, Function<List<InsumoDI1>, CurvaJuros>> implementacoesPorCodigo;

    public BuiltinModeloConstrucao(CurveBootstrapper curveBootstrapper) {
        this.implementacoesPorCodigo = Map.of(
                "PRE_DI1_B3", curveBootstrapper::montarCurvaPreDeDi1
        );
    }

    @Override
    public boolean suporta(ModeloCurva modelo) {
        return modelo.tipo() == TipoModelo.BUILTIN;
    }

    @Override
    public CurvaJuros construir(ModeloCurva modelo, List<InsumoDI1> insumos) {
        Function<List<InsumoDI1>, CurvaJuros> implementacao = implementacoesPorCodigo.get(modelo.codigo());
        if (implementacao == null) {
            throw new IllegalStateException("modelo embutido desconhecido: " + modelo.codigo());
        }
        return implementacao.apply(insumos);
    }
}
