package com.poccurves.engine.application.model;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Registro de ModeloCurva por código. Resolver um código não registrado
 * lança exceção nomeando-o.
 */
public final class CatalogoModelos {

    private final Map<String, ModeloCurva> porCodigo;

    /**
     * @throws NullPointerException     se modelos for nulo
     * @throws IllegalArgumentException se dois modelos declararem o mesmo código
     */
    public CatalogoModelos(List<ModeloCurva> modelos) {
        Objects.requireNonNull(modelos, "modelos não pode ser nulo");
        Map<String, ModeloCurva> mapa = new HashMap<>();
        for (ModeloCurva modelo : modelos) {
            String codigo = modelo.codigo();
            if (mapa.containsKey(codigo)) {
                throw new IllegalArgumentException("código de modelo duplicado no catálogo: " + codigo);
            }
            mapa.put(codigo, modelo);
        }
        this.porCodigo = Map.copyOf(mapa);
    }

    /**
     * Resolve o modelo registrado para o código informado.
     *
     * @throws IllegalArgumentException se não houver modelo registrado para esse código
     */
    public ModeloCurva resolver(String codigo) {
        ModeloCurva modelo = porCodigo.get(codigo);
        if (modelo == null) {
            throw new IllegalArgumentException("modelo não encontrado para código: " + codigo);
        }
        return modelo;
    }
}
