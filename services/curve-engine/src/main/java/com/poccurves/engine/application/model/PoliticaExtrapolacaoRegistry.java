package com.poccurves.engine.application.model;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Registro de PoliticaExtrapolacao por identificador. Resolver um
 * identificador inexistente lança exceção nomeando-o — mesmo padrão de
 * {@link InterpoladorRegistry}.
 */
public final class PoliticaExtrapolacaoRegistry {

    private final Map<String, PoliticaExtrapolacao> porIdentificador;

    /**
     * @throws NullPointerException     se politicas for nulo
     * @throws IllegalArgumentException se duas políticas declararem o mesmo identificador()
     */
    public PoliticaExtrapolacaoRegistry(List<PoliticaExtrapolacao> politicas) {
        Objects.requireNonNull(politicas, "politicas não pode ser nulo");
        Map<String, PoliticaExtrapolacao> mapa = new HashMap<>();
        for (PoliticaExtrapolacao politica : politicas) {
            String id = politica.identificador();
            if (mapa.containsKey(id)) {
                throw new IllegalArgumentException("identificador de política de extrapolação duplicado: " + id);
            }
            mapa.put(id, politica);
        }
        this.porIdentificador = Map.copyOf(mapa);
    }

    /**
     * Resolve a política registrada para o identificador informado.
     *
     * @throws IllegalArgumentException se não houver política registrada para esse identificador
     */
    public PoliticaExtrapolacao resolver(String identificador) {
        PoliticaExtrapolacao politica = porIdentificador.get(identificador);
        if (politica == null) {
            throw new IllegalArgumentException("política de extrapolação não encontrada para identificador: " + identificador);
        }
        return politica;
    }
}
