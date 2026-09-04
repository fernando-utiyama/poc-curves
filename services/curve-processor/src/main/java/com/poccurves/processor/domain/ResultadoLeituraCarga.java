package com.poccurves.processor.domain;


import java.util.List;
import java.util.Objects;

/**
 * Resultado de duas formas da leitura de um arquivo de carga manual de
 * curva (CSV ou planilha): sucesso com todos os vértices, ou falha com
 * TODOS os erros encontrados no arquivo inteiro (nunca aplicação parcial —
 * tarefa 6.4 do backlog).
 */
public sealed interface ResultadoLeituraCarga {

    record Sucesso(List<VerticeCurva> vertices) implements ResultadoLeituraCarga {
        public Sucesso {
            Objects.requireNonNull(vertices, "vertices não pode ser nulo");
        }
    }

    record Falha(List<ErroLinhaCarga> erros) implements ResultadoLeituraCarga {
        public Falha {
            Objects.requireNonNull(erros, "erros não pode ser nulo");
            if (erros.isEmpty()) {
                throw new IllegalArgumentException("erros não pode ser vazio numa Falha");
            }
        }
    }
}
