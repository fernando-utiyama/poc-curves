package com.poccurves.engine.application.service;
import com.poccurves.engine.application.model.CurvaJuros;
import com.poccurves.engine.application.model.InsumoDI1;
import com.poccurves.engine.application.model.ModeloCurva;
import com.poccurves.engine.application.port.ModeloConstrucaoPort;


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
