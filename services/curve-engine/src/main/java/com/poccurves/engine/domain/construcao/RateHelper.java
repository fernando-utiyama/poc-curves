package com.poccurves.engine.domain.construcao;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Classe utilitária para extração e validação de taxas de insumos de mercado.
 */
public final class RateHelper {

    private RateHelper() {
        // impede instanciação
    }

    /**
     * Extrai e valida a taxa de um insumo DI1.
     * 
     * @param insumo o insumo DI1
     * @return a taxa validada
     * @throws IllegalArgumentException se a taxa for nula ou <= -1
     * @throws NullPointerException se o insumo for nulo
     */
    public static BigDecimal taxaDi1(InsumoDI1 insumo) {
        Objects.requireNonNull(insumo, "insumo não pode ser nulo");
        BigDecimal taxa = insumo.taxaAjuste();
        if (taxa == null) {
            throw new IllegalArgumentException("taxa nula no insumo " + insumo.ticker());
        }
        if (taxa.compareTo(BigDecimal.valueOf(-1)) <= 0) {
            throw new IllegalArgumentException("taxa inválida no insumo " + insumo.ticker() + ", deve ser > -1: " + taxa);
        }
        return taxa;
    }

    /**
     * Extrai e valida a taxa do CDI, já anualizada base 252 (mesma convenção de {@link #taxaDi1}).
     * <p>
     * A fonte de dado existe desde a seção 9 de {@code openspec/changes/feeder-marketdata/tasks.md}
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
