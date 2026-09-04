package com.poccurves.processor.domain;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Registro de DatasetParser por identificador de dataset. Resolver um
 * dataset não registrado retorna Optional.empty() — é o sinal para o
 * consumidor encaminhar a mensagem para a dead-letter UNKNOWN_DATASET.
 */
public final class DatasetParserRegistry {

    private final Map<String, DatasetParser> parsersPorDataset;

    /**
     * @throws NullPointerException     se parsers for nulo
     * @throws IllegalArgumentException se dois parsers declararem o mesmo dataset()
     */
    public DatasetParserRegistry(List<DatasetParser> parsers) {
        Objects.requireNonNull(parsers, "parsers não pode ser nulo");
        Map<String, DatasetParser> mapa = new HashMap<>();
        for (DatasetParser parser : parsers) {
            String dataset = parser.dataset();
            if (mapa.containsKey(dataset)) {
                throw new IllegalArgumentException("dataset duplicado no registro de parsers: " + dataset);
            }
            mapa.put(dataset, parser);
        }
        this.parsersPorDataset = Map.copyOf(mapa);
    }

    /** Resolve o parser registrado para o dataset informado, se houver. */
    public Optional<DatasetParser> resolver(String dataset) {
        return Optional.ofNullable(parsersPorDataset.get(dataset));
    }
}
