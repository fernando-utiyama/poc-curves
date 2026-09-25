package com.poccurves.engine.application.construcao;

import java.math.BigDecimal;

/**
 * Classe utilitária para extração e validação de taxas de insumos de mercado.
 */
public final class RateHelper {

    private RateHelper() {
        // impede instanciação
    }

    /**
     * Extrai e valida a taxa do CDI, já anualizada base 252.
     * <p>
     * A fonte de dado existe desde a seção 9 de {@code openspec/changes/function-marketdata/tasks.md}
     * (feeder BCB, série 4389 — "Taxa de juros - CDI anualizada base 252") — não é mais um
     * bloqueio de dado. O que ainda falta é a ingestão automática desses eventos {@code source: BCB}
     * em {@code ponto_dado_mercado} (parser análogo a {@code Bvbg086PricRptParser} do
     * curve-processor, fora do escopo deste serviço) — até essa plumbing existir, quem chamar este
     * helper monta o {@link BigDecimal} a partir do valor bruto publicado pelo feeder
     * (string percentual tipo {@code "13.90"}, convertida para fração decimal {@code 0.1390} antes
     * de chamar este método — o feeder nunca converte, por design).
     *
     * @param taxa a taxa do CDI, como fração decimal anualizada base 252 (ex.: 0.1390 para 13,90% a.a.)
     * @return a taxa validada
     * @throws IllegalArgumentException se a taxa for nula ou <= -1
     */
    public static BigDecimal taxaCdi(BigDecimal taxa) {
        if (taxa == null) {
            throw new IllegalArgumentException("taxa CDI não pode ser nula");
        }
        if (taxa.compareTo(BigDecimal.valueOf(-1)) <= 0) {
            throw new IllegalArgumentException("taxa CDI inválida, deve ser > -1: " + taxa);
        }
        return taxa;
    }

    /**
     * Extrai a taxa de inflação implícita.
     * <p>
     * Bloqueio real (fato 6): não existe nenhum dataset de inflação implícita definido no sistema.
     * 
     * @return a taxa de inflação implícita
     * @throws UnsupportedOperationException sempre, pois a fonte de dado não existe ainda no sistema
     */
    public static BigDecimal taxaInflacaoImplicita() {
        throw new UnsupportedOperationException("fonte de dado para inflação implícita não existe ainda no sistema");
    }
}
