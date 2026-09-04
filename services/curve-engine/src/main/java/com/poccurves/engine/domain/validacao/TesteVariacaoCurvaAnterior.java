package com.poccurves.engine.domain.validacao;


import java.math.BigDecimal;

/**
 * Valida a variação absoluta da taxa de cada vértice em relação à curva do dia anterior
 * para o mesmo prazo. Se os prazos forem disjuntos, o teste não é aplicável.
 * A medidaObservada reporta a maior variação absoluta encontrada.
 */
public class TesteVariacaoCurvaAnterior implements TesteValidacao {

    @Override
    public String identificador() {
        return "VARIACAO_CURVA_ANTERIOR";
    }

    @Override
    public ResultadoTeste executar(ContextoValidacao contexto, BigDecimal limite) {
        if (contexto.curvaDiaAnterior().isEmpty()) {
            return new ResultadoTeste(identificador(), Classificacao.BLOQUEANTE, ResultadoValidacao.NAO_APLICAVEL, null, limite, "Curva do dia anterior não disponível");
        }

        var curvaAnterior = contexto.curvaDiaAnterior().get();
        BigDecimal maiorVariacao = BigDecimal.ZERO;
        boolean violou = false;
        String detalhe = "Variações em relação à curva anterior dentro do limite";
        boolean algumPrazoComparado = false;

        for (var vertice : contexto.curvaConstruida().vertices()) {
            BigDecimal taxaAnterior;
            try {
                taxaAnterior = curvaAnterior.taxaEm(vertice.prazoDiasUteis());
            } catch (IllegalArgumentException e) {
                // Prazo não existe na curva do dia anterior, pula
                continue;
            }

            algumPrazoComparado = true;
            BigDecimal variacao = vertice.taxa().subtract(taxaAnterior).abs();

            if (variacao.compareTo(maiorVariacao) > 0) {
                maiorVariacao = variacao;
            }

            if (!violou && variacao.compareTo(limite) > 0) {
                violou = true;
                detalhe = String.format("Variação de %s no prazo %d (taxa atual: %s, taxa anterior: %s) excede o limite", 
                    variacao, vertice.prazoDiasUteis(), vertice.taxa(), taxaAnterior);
            }
        }

        if (!algumPrazoComparado) {
            return new ResultadoTeste(identificador(), Classificacao.BLOQUEANTE, ResultadoValidacao.NAO_APLICAVEL, null, limite, "Nenhum prazo correspondente encontrado na curva do dia anterior");
        }

        if (violou) {
            return new ResultadoTeste(identificador(), Classificacao.BLOQUEANTE, ResultadoValidacao.REPROVADO, maiorVariacao, limite, detalhe);
        }

        return new ResultadoTeste(identificador(), Classificacao.BLOQUEANTE, ResultadoValidacao.APROVADO, maiorVariacao, limite, detalhe);
    }
}
