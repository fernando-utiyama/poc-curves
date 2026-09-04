package com.poccurves.engine.application.validator;
import com.poccurves.engine.application.model.Classificacao;
import com.poccurves.engine.application.model.ContextoValidacao;
import com.poccurves.engine.application.model.ResultadoTeste;
import com.poccurves.engine.application.model.ResultadoValidacao;
import com.poccurves.engine.application.model.RoundingPolicy;


import java.math.BigDecimal;
import java.math.MathContext;

/**
 * Valida o limite de taxa forward implícita entre pares de vértices adjacentes.
 * A medidaObservada reporta a maior taxa forward encontrada entre todos os pares.
 */
public class TesteLimiteTaxaForward implements TesteValidacao {

    @Override
    public String identificador() {
        return "LIMITE_TAXA_FORWARD";
    }

    @Override
    public ResultadoTeste executar(ContextoValidacao contexto, BigDecimal limite) {
        var vertices = contexto.curvaConstruida().vertices();
        if (vertices.size() <= 1) {
            return new ResultadoTeste(identificador(), Classificacao.BLOQUEANTE, ResultadoValidacao.NAO_APLICAVEL, null, limite, "Sem pares para comparar");
        }

        BigDecimal maiorTaxa = null;
        boolean violou = false;
        String detalhe = "Taxas forward dentro do limite";

        BigDecimal dfAnterior = null;
        int prazoAnterior = -1;

        for (var vertice : vertices) {
            BigDecimal base = BigDecimal.ONE.add(vertice.taxa());
            BigDecimal expoente = BigDecimal.valueOf(-vertice.prazoDiasUteis()).divide(BigDecimal.valueOf(252), MathContext.DECIMAL128);
            BigDecimal dfAtual = RoundingPolicy.powerRaw(base, expoente, MathContext.DECIMAL128);

            if (dfAnterior != null) {
                BigDecimal razaoDf = dfAnterior.divide(dfAtual, MathContext.DECIMAL128);
                BigDecimal expoenteFwd = BigDecimal.valueOf(252).divide(BigDecimal.valueOf(vertice.prazoDiasUteis() - prazoAnterior), MathContext.DECIMAL128);
                
                BigDecimal fwd = RoundingPolicy.powerRaw(razaoDf, expoenteFwd, MathContext.DECIMAL128).subtract(BigDecimal.ONE);
                
                if (maiorTaxa == null || fwd.compareTo(maiorTaxa) > 0) {
                    maiorTaxa = fwd;
                }

                if (!violou && fwd.compareTo(limite) > 0) {
                    violou = true;
                    detalhe = String.format("Taxa forward de %s entre os prazos %d e %d excede o limite", 
                        fwd, prazoAnterior, vertice.prazoDiasUteis());
                }
            }

            dfAnterior = dfAtual;
            prazoAnterior = vertice.prazoDiasUteis();
        }

        if (violou) {
            return new ResultadoTeste(identificador(), Classificacao.BLOQUEANTE, ResultadoValidacao.REPROVADO, maiorTaxa, limite, detalhe);
        }

        return new ResultadoTeste(identificador(), Classificacao.BLOQUEANTE, ResultadoValidacao.APROVADO, maiorTaxa, limite, detalhe);
    }
}
