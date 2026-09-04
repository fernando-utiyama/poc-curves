package com.poccurves.engine.domain.validacao;


import java.math.BigDecimal;

public class TesteEstrutural implements TesteValidacao {

    @Override
    public String identificador() {
        return "ESTRUTURAL";
    }

    @Override
    public ResultadoTeste executar(ContextoValidacao contexto, BigDecimal limite) {
        var vertices = contexto.curvaConstruida().vertices();
        if (vertices.isEmpty()) {
            return new ResultadoTeste(identificador(), Classificacao.BLOQUEANTE, ResultadoValidacao.REPROVADO, BigDecimal.ZERO, limite, "Curva não possui vértices");
        }

        int prazoAnterior = -1;
        for (var vertice : vertices) {
            if (vertice.prazoDiasUteis() <= prazoAnterior) {
                return new ResultadoTeste(identificador(), Classificacao.BLOQUEANTE, ResultadoValidacao.REPROVADO, new BigDecimal(vertices.size()), limite, "Prazos não são estritamente crescentes");
            }
            if (vertice.taxa() == null) {
                return new ResultadoTeste(identificador(), Classificacao.BLOQUEANTE, ResultadoValidacao.REPROVADO, new BigDecimal(vertices.size()), limite, "Vértice com taxa nula encontrado no prazo " + vertice.prazoDiasUteis());
            }
            prazoAnterior = vertice.prazoDiasUteis();
        }

        BigDecimal cobertura = new BigDecimal(vertices.size());
        if (cobertura.compareTo(limite) < 0) {
            return new ResultadoTeste(identificador(), Classificacao.BLOQUEANTE, ResultadoValidacao.REPROVADO, cobertura, limite, "Contagem de vértices (" + vertices.size() + ") menor que a exigida (" + limite.intValue() + ")");
        }

        return new ResultadoTeste(identificador(), Classificacao.BLOQUEANTE, ResultadoValidacao.APROVADO, cobertura, limite, "Estrutura válida e com cobertura suficiente");
    }
}
