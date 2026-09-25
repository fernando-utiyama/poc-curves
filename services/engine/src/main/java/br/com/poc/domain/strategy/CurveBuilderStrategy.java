package br.com.poc.domain.strategy;

import br.com.poc.domain.model.ContextoConstrucaoCurva;
import br.com.poc.domain.model.VerticeCalculado;
import br.com.poc.domain.model.VerticeInsumo;

import java.util.List;

public interface CurveBuilderStrategy {

    String getStrategyName();

    List<VerticeCalculado> build(ContextoConstrucaoCurva contexto, List<VerticeInsumo> insumos);
}
