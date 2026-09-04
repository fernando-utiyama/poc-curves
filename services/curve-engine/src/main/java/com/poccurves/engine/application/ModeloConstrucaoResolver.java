package com.poccurves.engine.application;
import com.poccurves.engine.domain.construcao.InsumoDI1;
import com.poccurves.engine.domain.construcao.ModeloCurva;
import com.poccurves.engine.domain.curva.CurvaJuros;


import java.util.List;

/**
 * Despacha a construção para a implementação de {@link ModeloConstrucaoPort} correta
 * (BUILTIN vs. GROOVY) — tarefa 7.1 do backlog curve-engine.
 */
public class ModeloConstrucaoResolver {

    private final List<ModeloConstrucaoPort> implementacoes;

    public ModeloConstrucaoResolver(List<ModeloConstrucaoPort> implementacoes) {
        this.implementacoes = implementacoes;
    }

    public CurvaJuros construir(ModeloCurva modelo, List<InsumoDI1> insumos) {
        for (ModeloConstrucaoPort implementacao : implementacoes) {
            if (implementacao.suporta(modelo)) {
                return implementacao.construir(modelo, insumos);
            }
        }
        throw new IllegalStateException("nenhuma implementação de construção suporta o tipo de modelo: " + modelo.tipo());
    }
}
