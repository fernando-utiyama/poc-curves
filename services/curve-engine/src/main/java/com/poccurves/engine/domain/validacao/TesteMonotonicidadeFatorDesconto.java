package com.poccurves.engine.domain.validacao;
import com.poccurves.engine.domain.curva.RoundingPolicy;


import java.math.BigDecimal;
import java.math.MathContext;

/**
 * Valida a monotonicidade do fator de desconto calculado para os vértices da curva.
 * A medidaObservada reporta a maior diferença (DF(i+1) - DF(i)) encontrada entre todos os pares,
 * mesmo que essa diferença seja menor que o limite ou negativa (indicando redução normal).
 */
public class TesteMonotonicidadeFatorDesconto implements TesteValidacao {

    @Override
    public String identificador() {
        return "MONOTONICIDADE_FATOR_DESCONTO";
    }

    @Override
    public ResultadoTeste executar(ContextoValidacao contexto, BigDecimal limite) {
        var vertices = contexto.curvaConstruida().vertices();
        if (vertices.size() <= 1) {
            return new ResultadoTeste(identificador(), Classificacao.BLOQUEANTE, ResultadoValidacao.NAO_APLICAVEL, null, limite, "Sem pares para comparar");
        }

        BigDecimal maiorViolacao = BigDecimal.ZERO;
        boolean violou = false;
        String detalhe = "Fatores de desconto monotonicamente decrescentes dentro do limite";

        BigDecimal dfAnterior = null;
        int prazoAnterior = -1;

        for (var vertice : vertices) {
            BigDecimal base = BigDecimal.ONE.add(vertice.taxa());
            BigDecimal expoente = BigDecimal.valueOf(-vertice.prazoDiasUteis()).divide(BigDecimal.valueOf(252), MathContext.DECIMAL128);
            BigDecimal dfAtual = RoundingPolicy.powerRaw(base, expoente, MathContext.DECIMAL128);

            if (dfAnterior != null) {
                BigDecimal aumento = dfAtual.subtract(dfAnterior);
                
                if (aumento.compareTo(maiorViolacao) > 0) {
                    maiorViolacao = aumento;
                }

                if (!violou && aumento.compareTo(limite) > 0) {
                    violou = true;
                    detalhe = String.format("Aumento no fator de desconto de %s no prazo %d para %s no prazo %d excede o limite (diferença: %s)", 
                        dfAnterior, prazoAnterior, dfAtual, vertice.prazoDiasUteis(), aumento);
                }
            }

            dfAnterior = dfAtual;
            prazoAnterior = vertice.prazoDiasUteis();
        }

        if (violou) {
            return new ResultadoTeste(identificador(), Classificacao.BLOQUEANTE, ResultadoValidacao.REPROVADO, maiorViolacao, limite, detalhe);
        }

        return new ResultadoTeste(identificador(), Classificacao.BLOQUEANTE, ResultadoValidacao.APROVADO, maiorViolacao, limite, detalhe);
    }
}
