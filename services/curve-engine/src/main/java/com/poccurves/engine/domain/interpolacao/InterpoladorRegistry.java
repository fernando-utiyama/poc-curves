package com.poccurves.engine.domain.interpolacao;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Registro de Interpolador por identificador. Resolver um identificador
 * inexistente lança exceção nomeando-o — é o requisito do backlog
 * ("falha nomeada para identificador inexistente").
 */
public final class InterpoladorRegistry {

    private final Map<String, Interpolador> porIdentificador;

    /**
     * @throws NullPointerException     se interpoladores for nulo
     * @throws IllegalArgumentException se dois interpoladores declararem o mesmo identificador()
     */
    public InterpoladorRegistry(List<Interpolador> interpoladores) {
        Objects.requireNonNull(interpoladores, "interpoladores não pode ser nulo");
        Map<String, Interpolador> mapa = new HashMap<>();
        for (Interpolador interpolador : interpoladores) {
            String id = interpolador.identificador();
            if (mapa.containsKey(id)) {
                throw new IllegalArgumentException("identificador de interpolador duplicado: " + id);
            }
            mapa.put(id, interpolador);
        }
        this.porIdentificador = Map.copyOf(mapa);
    }

    /**
     * Resolve o interpolador registrado para o identificador informado.
     *
     * @throws IllegalArgumentException se não houver interpolador registrado para esse identificador
     */
    public Interpolador resolver(String identificador) {
        Interpolador interpolador = porIdentificador.get(identificador);
        if (interpolador == null) {
            throw new IllegalArgumentException("interpolador não encontrado para identificador: " + identificador);
        }
        return interpolador;
    }
}
