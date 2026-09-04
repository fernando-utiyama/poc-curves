package com.poccurves.engine.domain.construcao;
import com.poccurves.engine.domain.curva.CurvaJuros;
import com.poccurves.engine.domain.curva.Vertice;


import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Montador de curvas de juros a partir de insumos.
 * <p>
 * Decisão de design (Fato 5): como cada contrato DI1 já é um ponto de taxa
 * zero-cupom direto, NÃO existe iteração/convergência numérica real neste
 * modelo embutido específico. A montagem da curva PRE a partir dos contratos
 * DI1 é uma montagem direta. A falha de não convergência aplica-se apenas
 * como validação de conjunto insuficiente.
 */
public final class CurveBootstrapper {

    /**
     * Monta uma curva PRE a partir de insumos de DI1.
     * <p>
     * Insumos com taxaAjuste ou diasUteisVencimento nulos são ignorados
     * silenciosamente, pois significa que o contrato não teve negociação ou
     * cadastro completo no dia (uma possível ausência na intersecção dos datasets).
     * 
     * @param insumos lista de insumos DI1
     * @return a curva construída
     * @throws IllegalArgumentException se insumos for vazio ou nenhum insumo for montável
     */
    public CurvaJuros montarCurvaPreDeDi1(List<InsumoDI1> insumos) {
        Objects.requireNonNull(insumos, "insumos não pode ser nulo");
        if (insumos.isEmpty()) {
            throw new IllegalArgumentException("conjunto insuficiente: recebidos 0 insumos (exige ao menos 1)");
        }

        List<Vertice> vertices = new ArrayList<>();
        for (InsumoDI1 insumo : insumos) {
            if (insumo.taxaAjuste() == null || insumo.diasUteisVencimento() == null) {
                continue;
            }

            BigDecimal taxa = RateHelper.taxaDi1(insumo);

            // Montagem direta (ver Javadoc da classe) — prazoDiasCorridos e fatorDesconto
            // ficam nulos aqui; quem precisar deles calcula a partir de prazoDiasUteis/taxa.
            vertices.add(new Vertice(
                    insumo.diasUteisVencimento(),
                    null,
                    insumo.dataVencimento(),
                    taxa,
                    null
            ));
        }

        if (vertices.isEmpty()) {
            throw new IllegalArgumentException("conjunto insuficiente: nenhum insumo com taxa e prazo válidos entre os " + insumos.size() + " recebidos");
        }

        return CurvaJuros.de(vertices);
    }
}
