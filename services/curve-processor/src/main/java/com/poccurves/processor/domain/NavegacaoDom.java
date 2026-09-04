package com.poccurves.processor.domain;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Navegação estrutural por filho direto e nome de tag, deliberadamente sem
 * XPath: os fragmentos BizGrp reais da B3 repetem o mesmo nome de tag
 * ({@code Id}) em profundidades diferentes (cabeçalho AppHdr vs. corpo do
 * instrumento) — navegar por caminho de filhos evita pegar o {@code Id}
 * errado, o que {@code getElementsByTagName} (busca em toda a subárvore)
 * não garante.
 */
final class NavegacaoDom {

    private NavegacaoDom() {
    }

    /** Primeiro filho direto de {@code pai} com o nome de tag informado, ou {@code null} se não houver. */
    static Element filhoDireto(Element pai, String nomeTag) {
        if (pai == null) {
            return null;
        }
        NodeList filhos = pai.getChildNodes();
        for (int i = 0; i < filhos.getLength(); i++) {
            Node filho = filhos.item(i);
            if (filho.getNodeType() == Node.ELEMENT_NODE && nomeTag.equals(filho.getNodeName())) {
                return (Element) filho;
            }
        }
        return null;
    }

    /** Navega por uma cadeia de filhos diretos, nível a nível; retorna {@code null} se qualquer nível faltar. */
    static Element caminho(Element raiz, String... nomesTag) {
        Element atual = raiz;
        for (String nomeTag : nomesTag) {
            atual = filhoDireto(atual, nomeTag);
            if (atual == null) {
                return null;
            }
        }
        return atual;
    }

    /** Texto do elemento, ou {@code null} se o elemento não existir. */
    static String texto(Element elemento) {
        return elemento == null ? null : elemento.getTextContent();
    }
}
