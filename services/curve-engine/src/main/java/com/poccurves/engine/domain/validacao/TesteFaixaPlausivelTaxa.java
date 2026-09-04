package com.poccurves.engine.domain.validacao;


import java.math.BigDecimal;

/**
 * Valida que a taxa de todos os vértices está dentro do intervalo [0, limite].
 * A medidaObservada reporta a taxa encontrada mais distante de zero (em valor absoluto),
 * que evidencia o maior distanciamento da faixa de valores típica ou o maior estouro do limite.
 */
public class TesteFaixaPlausivelTaxa implements TesteValidacao {

    @Override
    public String identificador() {
        return "FAIXA_PLAUSIVEL_TAXA";
    }

    @Override
    public ResultadoTeste executar(ContextoValidacao contexto, BigDecimal limite) {
        var vertices = contexto.curvaConstruida().vertices();
        if (vertices.isEmpty()) {
            return new ResultadoTeste(identificador(), Classificacao.BLOQUEANTE, ResultadoValidacao.NAO_APLICAVEL, null, limite, "Sem vértices para validar");
        }

        BigDecimal taxaMaisExtrema = null;
        BigDecimal maiorDistanciaZero = BigDecimal.valueOf(-1);
        boolean violou = false;
        String detalhe = "Todas as taxas dentro da faixa plausível [0, " + limite + "]";

        for (var vertice : vertices) {
            BigDecimal taxa = vertice.taxa();
            BigDecimal absTaxa = taxa.abs();
            
            if (absTaxa.compareTo(maiorDistanciaZero) > 0) {
                maiorDistanciaZero = absTaxa;
                taxaMaisExtrema = taxa;
            }

            if (!violou && (taxa.compareTo(BigDecimal.ZERO) < 0 || taxa.compareTo(limite) > 0)) {
                violou = true;
                detalhe = String.format("Taxa %s do prazo %d fora do intervalo [0, %s]", taxa, vertice.prazoDiasUteis(), limite);
            }
        }

        if (violou) {
            return new ResultadoTeste(identificador(), Classificacao.BLOQUEANTE, ResultadoValidacao.REPROVADO, taxaMaisExtrema, limite, detalhe);
        }

        return new ResultadoTeste(identificador(), Classificacao.BLOQUEANTE, ResultadoValidacao.APROVADO, taxaMaisExtrema, limite, detalhe);
    }
}
