package com.poccurves.processor.application.validator;

import com.poccurves.processor.application.model.VerticeCurva;

import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Compara os vértices de PRE extraídos do TaxaSwap.txt contra os vértices de PRE já publicados
 * via curva pronta de referência (B3_CURVA_PRE) — spec "Validação do layout por oráculo cruzado"
 * (openspec/changes/b3-additional-curves). Puro: não faz I/O, só compara duas listas já lidas.
 * <p>
 * Compara pela interseção de prazos (dias úteis) presentes nas duas fontes: os dois feeds podem
 * ter densidade de grid diferente (a spec não garante que os dois publiquem exatamente o mesmo
 * conjunto de vértices), então a garantia real e verificável é "onde os dois têm dado, o valor é
 * o mesmo" — não "os dois têm os mesmos prazos". Arredonda para {@value #CASAS_DECIMAIS_PRE}
 * casas decimais antes de comparar (Manual da B3: PRE publica com 3 casas), então uma diferença
 * de precisão na última casa implícita do layout não conta como divergência real.
 */
public final class OraculoTaxaSwapValidator {

    private static final int CASAS_DECIMAIS_PRE = 3;

    private OraculoTaxaSwapValidator() {
    }

    /** @return prazos (dias úteis) cujo valor diverge entre as duas fontes, na interseção dos dois conjuntos. */
    public static List<Integer> prazosDivergentes(List<VerticeCurva> extraidosDoTaxaSwap, List<VerticeCurva> oraculo) {
        Map<Integer, VerticeCurva> porPrazoExtraido = indexarPorPrazo(extraidosDoTaxaSwap);
        Map<Integer, VerticeCurva> porPrazoOraculo = indexarPorPrazo(oraculo);

        List<Integer> divergentes = new ArrayList<>();
        for (Map.Entry<Integer, VerticeCurva> entrada : porPrazoExtraido.entrySet()) {
            VerticeCurva doOraculo = porPrazoOraculo.get(entrada.getKey());
            if (doOraculo == null) {
                continue;
            }
            if (entrada.getValue().taxa().setScale(CASAS_DECIMAIS_PRE, RoundingMode.HALF_UP)
                    .compareTo(doOraculo.taxa().setScale(CASAS_DECIMAIS_PRE, RoundingMode.HALF_UP)) != 0) {
                divergentes.add(entrada.getKey());
            }
        }
        return divergentes;
    }

    /** @return true se nenhuma das duas listas tem vértice em comum para comparar (oráculo não aplicável). */
    public static boolean semPrazosEmComum(List<VerticeCurva> extraidosDoTaxaSwap, List<VerticeCurva> oraculo) {
        Map<Integer, VerticeCurva> porPrazoOraculo = indexarPorPrazo(oraculo);
        return extraidosDoTaxaSwap.stream().noneMatch(v -> porPrazoOraculo.containsKey(v.prazoDiasUteis()));
    }

    private static Map<Integer, VerticeCurva> indexarPorPrazo(List<VerticeCurva> vertices) {
        Map<Integer, VerticeCurva> mapa = new TreeMap<>();
        for (VerticeCurva vertice : vertices) {
            mapa.put(vertice.prazoDiasUteis(), vertice);
        }
        return mapa;
    }
}
