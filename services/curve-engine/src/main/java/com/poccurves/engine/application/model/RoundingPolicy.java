package com.poccurves.engine.application.model;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Política de arredondamento imutável para cálculos financeiros de precisão arbitrária.
 */
public final class RoundingPolicy {

    private final int scale;
    private final RoundingMode mode;

    private RoundingPolicy(int scale, RoundingMode mode) {
        this.scale = scale;
        this.mode = mode;
    }

    /**
     * Cria uma política de arredondamento com a escala e modo especificados.
     *
     * @param scale a escala de casas decimais (deve ser >= 0)
     * @param mode  o modo de arredondamento
     * @return nova instância de {@link RoundingPolicy}
     * @throws IllegalArgumentException se scale for negativo
     * @throws NullPointerException     se mode for nulo
     */
    public static RoundingPolicy of(int scale, RoundingMode mode) {
        if (scale < 0) {
            throw new IllegalArgumentException("scale não pode ser negativo: " + scale);
        }
        Objects.requireNonNull(mode, "mode não pode ser nulo");
        return new RoundingPolicy(scale, mode);
    }

    /**
     * Atalho para {@link #of(int, RoundingMode)} utilizando {@link RoundingMode#DOWN}.
     * <p>
     * {@link RoundingMode#DOWN} é o modo de truncamento em Java: descarta os dígitos além da escala
     * sem arredondar para o vizinho mais próximo e nunca aumenta a magnitude do valor (ao contrário de
     * {@link RoundingMode#FLOOR}, que arredonda em direção a menos infinito e mudaria o sinal do
     * truncamento para valores negativos) — por isso {@code DOWN}, não {@code FLOOR}, é o modo certo
     * para "truncamento" no sentido financeiro (truncar -1.239 para escala 2 deve dar -1.23, não -1.24).
     *
     * @param scale a escala de casas decimais (deve ser >= 0)
     * @return nova instância de {@link RoundingPolicy} com truncamento (RoundingMode.DOWN)
     */
    public static RoundingPolicy truncating(int scale) {
        return of(scale, RoundingMode.DOWN);
    }

    /**
     * Retorna a escala de casas decimais desta política.
     *
     * @return a escala
     */
    public int scale() {
        return this.scale;
    }

    /**
     * Retorna o modo de arredondamento desta política.
     *
     * @return o {@link RoundingMode}
     */
    public RoundingMode mode() {
        return this.mode;
    }

    /**
     * Aplica a escala e o modo de arredondamento ao valor decimal fornecido.
     *
     * @param value o valor decimal a ser arredondado/truncado
     * @return novo {@link BigDecimal} com a escala e arredondamento aplicados
     * @throws NullPointerException se value for nulo
     */
    public BigDecimal apply(BigDecimal value) {
        Objects.requireNonNull(value, "value não pode ser nulo");
        return value.setScale(this.scale, this.mode);
    }

    /**
     * Calcula {@code base^expoente} e aplica esta política de arredondamento ao resultado final.
     * <p>
     * Exceção deliberada e única à regra "nunca double" do projeto (design.md, decisão D8): a
     * potenciação com expoente fracionário é, na prática, sempre calculada em double — é assim que
     * toda biblioteca numérica faz, inclusive as que produzem taxa de referência de mercado. O que a
     * regra do projeto protege é o valor de mercado armazenado e comparado (taxa, preço, fator já
     * publicado); esta função é o próprio cálculo de potência, e usar double aqui é a prática padrão,
     * não um atalho. O resultado é convertido para {@code BigDecimal} e passa pela política de
     * arredondamento antes de ser devolvido — nenhum outro método desta classe toca double.
     *
     * @param base        a base da potenciação
     * @param expoente    o expoente, inteiro ou fracionário (ex.: {@code -diasUteis/base} no fator de desconto)
     * @param mathContext o contexto matemático usado para converter o double resultante em BigDecimal
     * @return o resultado da potenciação arredondado conforme esta política
     * @throws NullPointerException se base, expoente ou mathContext forem nulos
     * @throws ArithmeticException  se o resultado não for finito (ex.: overflow, base negativa com
     *                              expoente fracionário)
     */
    public BigDecimal power(BigDecimal base, BigDecimal expoente, MathContext mathContext) {
        Objects.requireNonNull(base, "base não pode ser nula");
        Objects.requireNonNull(expoente, "expoente não pode ser nulo");
        Objects.requireNonNull(mathContext, "mathContext não pode ser nulo");

        double resultado = Math.pow(base.doubleValue(), expoente.doubleValue());
        if (!Double.isFinite(resultado)) {
            throw new ArithmeticException(
                    "power produziu resultado não finito para base=" + base + ", expoente=" + expoente);
        }

        return this.apply(new BigDecimal(resultado, mathContext));
    }

    /**
     * Calcula {@code base^expoente} sem aplicar política de truncamento, usando a precisão de MathContext.
     * <p>
     * Exceção deliberada e única à regra "nunca double" do projeto (design.md, decisão D8):
     * interpoladores fora deste arquivo (que não têm uma instância de RoundingPolicy, só um MathContext)
     * precisam da mesma potenciação com expoente fracionário para converter taxa em fator de desconto
     * e vice-versa. Por isso, este método fica aqui e não num arquivo novo. É a mesma exceção de power.
     *
     * @param base        a base da potenciação
     * @param expoente    o expoente, inteiro ou fracionário
     * @param mathContext o contexto matemático usado para converter o double resultante em BigDecimal
     * @return o resultado da potenciação
     * @throws NullPointerException se base, expoente ou mathContext forem nulos
     * @throws ArithmeticException  se o resultado não for finito
     */
    public static BigDecimal powerRaw(BigDecimal base, BigDecimal expoente, MathContext mathContext) {
        Objects.requireNonNull(base, "base não pode ser nula");
        Objects.requireNonNull(expoente, "expoente não pode ser nulo");
        Objects.requireNonNull(mathContext, "mathContext não pode ser nulo");

        double resultado = Math.pow(base.doubleValue(), expoente.doubleValue());
        if (!Double.isFinite(resultado)) {
            throw new ArithmeticException(
                    "powerRaw produziu resultado não finito para base=" + base + ", expoente=" + expoente);
        }

        return new BigDecimal(resultado, mathContext);
    }

    /**
     * Calcula {@code ln(valor)} (logaritmo natural). Mesma exceção documentada de {@link #powerRaw} —
     * não é uma segunda exceção à regra "nunca double", é o mesmo tipo de cálculo iterativo/transcendental
     * que não tem forma fechada em BigDecimal. Usado por interpoladores que operam em espaço logarítmico
     * (ex.: log-cúbico).
     *
     * @param valor       o valor do qual calcular o logaritmo natural — deve ser positivo
     * @param mathContext o contexto matemático usado para converter o double resultante em BigDecimal
     * @return {@code ln(valor)}
     * @throws NullPointerException     se valor ou mathContext forem nulos
     * @throws IllegalArgumentException se valor não for positivo
     * @throws ArithmeticException      se o resultado não for finito
     */
    public static BigDecimal lnRaw(BigDecimal valor, MathContext mathContext) {
        Objects.requireNonNull(valor, "valor não pode ser nulo");
        Objects.requireNonNull(mathContext, "mathContext não pode ser nulo");
        if (valor.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("ln exige valor positivo, recebido: " + valor);
        }

        double resultado = Math.log(valor.doubleValue());
        if (!Double.isFinite(resultado)) {
            throw new ArithmeticException("lnRaw produziu resultado não finito para valor=" + valor);
        }

        return new BigDecimal(resultado, mathContext);
    }

    /**
     * Calcula {@code e^expoente} (exponencial natural) — inverso de {@link #lnRaw}. Mesma exceção
     * documentada de {@link #powerRaw}.
     *
     * @param expoente    o expoente
     * @param mathContext o contexto matemático usado para converter o double resultante em BigDecimal
     * @return {@code e^expoente}
     * @throws NullPointerException se expoente ou mathContext forem nulos
     * @throws ArithmeticException  se o resultado não for finito
     */
    public static BigDecimal expRaw(BigDecimal expoente, MathContext mathContext) {
        Objects.requireNonNull(expoente, "expoente não pode ser nulo");
        Objects.requireNonNull(mathContext, "mathContext não pode ser nulo");

        double resultado = Math.exp(expoente.doubleValue());
        if (!Double.isFinite(resultado)) {
            throw new ArithmeticException("expRaw produziu resultado não finito para expoente=" + expoente);
        }

        return new BigDecimal(resultado, mathContext);
    }
}
