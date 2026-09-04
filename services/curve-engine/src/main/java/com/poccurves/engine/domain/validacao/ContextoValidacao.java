package com.poccurves.engine.domain.validacao;
import com.poccurves.engine.domain.construcao.InsumoDI1;
import com.poccurves.engine.domain.curva.CurvaJuros;


import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Contexto imutável entregue a um {@link TesteValidacao}: a curva construída mais os dados
 * externos que alguns testes de comparação precisam (reprecificação, variação contra o dia
 * anterior, comparação com curva importada) — resolvidos e entregues por quem orquestra a
 * bateria, nunca buscados pelo próprio teste (mantém o módulo de validação sem dependência de
 * banco nem broker, tarefa 8.1).
 */
public record ContextoValidacao(
        CurvaJuros curvaConstruida,
        List<InsumoDI1> insumosOriginais,
        Optional<CurvaJuros> curvaDiaAnterior,
        Optional<CurvaJuros> curvaImportadaMesmaData
) {
    public ContextoValidacao {
        Objects.requireNonNull(curvaConstruida, "curvaConstruida não pode ser nula");
        Objects.requireNonNull(insumosOriginais, "insumosOriginais não pode ser nulo (use lista vazia)");
        Objects.requireNonNull(curvaDiaAnterior, "curvaDiaAnterior não pode ser nulo (use Optional.empty())");
        Objects.requireNonNull(curvaImportadaMesmaData, "curvaImportadaMesmaData não pode ser nulo (use Optional.empty())");
    }
}
