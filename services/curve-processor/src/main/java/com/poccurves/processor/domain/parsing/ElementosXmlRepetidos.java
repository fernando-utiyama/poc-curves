package com.poccurves.processor.domain.parsing;

import java.util.ArrayList;
import java.util.List;

/**
 * Extrai substrings XML de cada ocorrência de {@code <nomeElemento>...</nomeElemento>}
 * no texto, sem parsing semântico — mesmo corte estrutural do D1b do
 * design.md aplicado no feeder (services/function-marketdata/src/xml-estrutural.ts),
 * reimplementado aqui porque o processor é Java e não compartilha código com
 * o feeder (Node/TS). Cada string retornada é um documento XML válido e
 * completo por si só (a tag raiz do fragmento), pronta para parsing DOM
 * individual.
 */
final class ElementosXmlRepetidos {

    private ElementosXmlRepetidos() {
    }

    /**
     * @throws IllegalArgumentException se houver uma tag de abertura sem a
     *                                   tag de fechamento correspondente
     */
    static List<String> extrair(String xml, String nomeElemento) {
        String tagAbertura = "<" + nomeElemento + ">";
        String tagFechamento = "</" + nomeElemento + ">";
        List<String> elementos = new ArrayList<>();
        int cursor = 0;

        while (true) {
            int inicio = xml.indexOf(tagAbertura, cursor);
            if (inicio == -1) {
                break;
            }
            int fim = xml.indexOf(tagFechamento, inicio);
            if (fim == -1) {
                throw new IllegalArgumentException(
                        "tag de abertura \"" + tagAbertura + "\" sem fechamento correspondente a partir da posição " + inicio);
            }
            int fimCompleto = fim + tagFechamento.length();
            elementos.add(xml.substring(inicio, fimCompleto));
            cursor = fimCompleto;
        }

        return elementos;
    }
}
