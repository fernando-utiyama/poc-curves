package com.poccurves.engine.application.port;
import com.poccurves.engine.application.construcao.CurvaJuros;
import com.poccurves.engine.application.construcao.Vertice;
import com.poccurves.engine.application.model.ModeloCurva;


import java.util.List;

/**
 * Estratégia de construção de curva a partir de um {@link ModeloCurva} — uma implementação
 * por {@link com.poccurves.engine.application.model.TipoModelo} (BUILTIN, GROOVY). Resolvida por
 * {@link com.poccurves.engine.application.service.ModeloConstrucaoResolver}.
 * <p>
 * {@code insumos} é genérico por curva — não é mais exclusivo de DI1 (openspec/changes/
 * b3-additional-curves): a mesma abstração serve tanto a curva DI1 histórica quanto as curvas
 * TS B3, cada uma alimentando pontos brutos já convertidos para {@link Vertice}.
 */
public interface ModeloConstrucaoPort {
    boolean suporta(ModeloCurva modelo);

    /** @throws com.poccurves.engine.application.exception.ModeloConstrucaoException em falha de compilação ou execução (só relevante para GROOVY) */
    CurvaJuros construir(ModeloCurva modelo, List<Vertice> insumos);
}
