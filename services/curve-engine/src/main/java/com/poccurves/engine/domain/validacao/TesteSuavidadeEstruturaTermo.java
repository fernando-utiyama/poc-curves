package com.poccurves.engine.domain.validacao;


import java.math.BigDecimal;
import java.math.MathContext;

/**
 * Valida a suavidade da curva comparando a diferença entre as inclinações (segunda diferença)
 * de trincas consecutivas de vértices.
 * A medidaObservada reporta a maior diferença absoluta de inclinação encontrada.
 */
public class TesteSuavidadeEstruturaTermo implements TesteValidacao {

    @Override
    public String identificador() {
        return "SUAVIDADE_ESTRUTURA_TERMO";
    }

    @Override
    public ResultadoTeste executar(ContextoValidacao contexto, BigDecimal limite) {
        var vertices = contexto.curvaConstruida().vertices();
        if (vertices.size() < 3) {
            return new ResultadoTeste(identificador(), Classificacao.BLOQUEANTE, ResultadoValidacao.NAO_APLICAVEL, null, limite, "Requer pelo menos 3 vértices para calcular a segunda diferença");
        }

        BigDecimal maiorDiferenca = BigDecimal.ZERO;
        boolean violou = false;
        String detalhe = "Diferenças de inclinação dentro do limite";

        for (int i = 0; i < vertices.size() - 2; i++) {
            var v1 = vertices.get(i);
            var v2 = vertices.get(i + 1);
            var v3 = vertices.get(i + 2);

            BigDecimal dTaxa1 = v2.taxa().subtract(v1.taxa());
            BigDecimal dPrazo1 = BigDecimal.valueOf(v2.prazoDiasUteis() - v1.prazoDiasUteis());
            BigDecimal inclinacao1 = dTaxa1.divide(dPrazo1, MathContext.DECIMAL128);

            BigDecimal dTaxa2 = v3.taxa().subtract(v2.taxa());
            BigDecimal dPrazo2 = BigDecimal.valueOf(v3.prazoDiasUteis() - v2.prazoDiasUteis());
            BigDecimal inclinacao2 = dTaxa2.divide(dPrazo2, MathContext.DECIMAL128);

            BigDecimal diferencaInclinacao = inclinacao1.subtract(inclinacao2).abs();

            if (diferencaInclinacao.compareTo(maiorDiferenca) > 0) {
                maiorDiferenca = diferencaInclinacao;
            }

            if (!violou && diferencaInclinacao.compareTo(limite) > 0) {
                violou = true;
                detalhe = String.format("Diferença de inclinação de %s nos prazos (%d, %d, %d) excede o limite", 
                    diferencaInclinacao, v1.prazoDiasUteis(), v2.prazoDiasUteis(), v3.prazoDiasUteis());
            }
        }

        if (violou) {
            return new ResultadoTeste(identificador(), Classificacao.BLOQUEANTE, ResultadoValidacao.REPROVADO, maiorDiferenca, limite, detalhe);
        }

        return new ResultadoTeste(identificador(), Classificacao.BLOQUEANTE, ResultadoValidacao.APROVADO, maiorDiferenca, limite, detalhe);
    }
}
