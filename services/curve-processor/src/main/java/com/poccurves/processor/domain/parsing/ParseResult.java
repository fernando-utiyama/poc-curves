package com.poccurves.processor.domain.parsing;
import com.poccurves.processor.domain.ingestao.PontoDadoMercado;

import java.util.List;
import java.util.Objects;

/**
 * Resultado de duas formas do parsing de um bloco de dado bruto: sucesso com
 * os pontos extraídos, ou falha nomeada (motivo resumido + diagnóstico
 * técnico) — é o que alimenta a dead-letter PARSE_FAILED no consumidor.
 */
public sealed interface ParseResult {

    record Sucesso(List<PontoDadoMercado> pontos) implements ParseResult {
        public Sucesso {
            Objects.requireNonNull(pontos, "pontos não pode ser nulo");
        }
    }

    record Falha(String motivo, String diagnostico) implements ParseResult {
        public Falha {
            if (motivo == null || motivo.isBlank()) {
                throw new IllegalArgumentException("motivo não pode ser nulo ou vazio");
            }
            Objects.requireNonNull(diagnostico, "diagnostico não pode ser nulo");
        }
    }
}
