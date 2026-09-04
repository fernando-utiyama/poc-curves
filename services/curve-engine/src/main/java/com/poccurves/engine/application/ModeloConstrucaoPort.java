package com.poccurves.engine.application;
import com.poccurves.engine.domain.construcao.InsumoDI1;
import com.poccurves.engine.domain.construcao.ModeloConstrucaoException;
import com.poccurves.engine.domain.construcao.ModeloCurva;
import com.poccurves.engine.domain.construcao.TipoModelo;
import com.poccurves.engine.domain.curva.CurvaJuros;


import java.util.List;

/**
 * Estratégia de construção de curva a partir de um {@link ModeloCurva} — uma implementação
 * por {@link com.poccurves.engine.domain.TipoModelo} (BUILTIN, GROOVY). Resolvida por
 * {@link ModeloConstrucaoResolver}.
 */
public interface ModeloConstrucaoPort {
    boolean suporta(ModeloCurva modelo);

    /** @throws com.poccurves.engine.domain.ModeloConstrucaoException em falha de compilação ou execução (só relevante para GROOVY) */
    CurvaJuros construir(ModeloCurva modelo, List<InsumoDI1> insumos);
}
