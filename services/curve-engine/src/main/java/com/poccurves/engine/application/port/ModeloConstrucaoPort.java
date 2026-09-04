package com.poccurves.engine.application.port;
import com.poccurves.engine.application.exception.ModeloConstrucaoException;
import com.poccurves.engine.application.model.CurvaJuros;
import com.poccurves.engine.application.model.InsumoDI1;
import com.poccurves.engine.application.model.ModeloCurva;
import com.poccurves.engine.application.model.TipoModelo;
import com.poccurves.engine.application.service.ModeloConstrucaoResolver;


import java.util.List;

/**
 * Estratégia de construção de curva a partir de um {@link ModeloCurva} — uma implementação
 * por {@link com.poccurves.engine.application.model.TipoModelo} (BUILTIN, GROOVY). Resolvida por
 * {@link ModeloConstrucaoResolver}.
 */
public interface ModeloConstrucaoPort {
    boolean suporta(ModeloCurva modelo);

    /** @throws com.poccurves.engine.application.exception.ModeloConstrucaoException em falha de compilação ou execução (só relevante para GROOVY) */
    CurvaJuros construir(ModeloCurva modelo, List<InsumoDI1> insumos);
}
