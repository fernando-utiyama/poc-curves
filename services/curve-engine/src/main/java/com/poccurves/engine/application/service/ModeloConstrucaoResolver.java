package com.poccurves.engine.application.service;
import com.poccurves.engine.application.construcao.CurvaJuros;
import com.poccurves.engine.application.construcao.Vertice;
import com.poccurves.engine.application.model.ModeloCurva;
import com.poccurves.engine.application.port.ModeloConstrucaoPort;


import java.util.List;

/**
 * Despacha a construção para a implementação de {@link ModeloConstrucaoPort} correta
 * (BUILTIN vs. GROOVY) — tarefa 7.1 do backlog curve-engine. Curva-agnóstico: serve tanto a
 * curva DI1 histórica quanto as curvas TS B3 (openspec/changes/b3-additional-curves).
 */
public class ModeloConstrucaoResolver {

    private final List<ModeloConstrucaoPort> implementacoes;

    public ModeloConstrucaoResolver(List<ModeloConstrucaoPort> implementacoes) {
        this.implementacoes = implementacoes;
    }

    public CurvaJuros construir(ModeloCurva modelo, List<Vertice> insumos) {
        for (ModeloConstrucaoPort implementacao : implementacoes) {
            if (implementacao.suporta(modelo)) {
                return implementacao.construir(modelo, insumos);
            }
        }
        throw new IllegalStateException("nenhuma implementação de construção suporta o tipo de modelo: " + modelo.tipo());
    }
}
