package com.poccurves.engine.application.validator;
import com.poccurves.engine.application.model.Classificacao;
import com.poccurves.engine.application.model.ContextoValidacao;
import com.poccurves.engine.application.model.ResultadoTeste;
import com.poccurves.engine.application.model.ResultadoValidacao;


import java.math.BigDecimal;

/**
 * Valida a variação absoluta da taxa de cada vértice em relação a uma curva importada da mesma data
 * para o mesmo prazo.
 * <p>
 * Hoje este teste é SEMPRE NAO_APLICAVEL na prática, porque não existe nenhuma curva IMPORTED
 * publicada em lugar nenhum do sistema ainda (bloqueio real e já documentado no backlog de curve-processor,
 * tarefa 5.1) — a lógica está pronta e correta, só não tem dado real para exercitar ainda.
 * A medidaObservada reporta a maior variação absoluta encontrada.
 */
public class TesteComparacaoCurvaImportada implements TesteValidacao {

    @Override
    public String identificador() {
        return "COMPARACAO_CURVA_IMPORTADA";
    }

    @Override
    public ResultadoTeste executar(ContextoValidacao contexto, BigDecimal limite) {
        if (contexto.curvaImportadaMesmaData().isEmpty()) {
            return new ResultadoTeste(identificador(), Classificacao.BLOQUEANTE, ResultadoValidacao.NAO_APLICAVEL, null, limite, "Curva importada não disponível");
        }

        var curvaImportada = contexto.curvaImportadaMesmaData().get();
        BigDecimal maiorVariacao = BigDecimal.ZERO;
        boolean violou = false;
        String detalhe = "Variações em relação à curva importada dentro do limite";
        boolean algumPrazoComparado = false;

        for (var vertice : contexto.curvaConstruida().vertices()) {
            BigDecimal taxaImportada;
            try {
                taxaImportada = curvaImportada.taxaEm(vertice.prazoDiasUteis());
            } catch (IllegalArgumentException e) {
                // Prazo não existe na curva importada, pula
                continue;
            }

            algumPrazoComparado = true;
            BigDecimal variacao = vertice.taxa().subtract(taxaImportada).abs();

            if (variacao.compareTo(maiorVariacao) > 0) {
                maiorVariacao = variacao;
            }

            if (!violou && variacao.compareTo(limite) > 0) {
                violou = true;
                detalhe = String.format("Variação de %s no prazo %d (taxa atual: %s, taxa importada: %s) excede o limite", 
                    variacao, vertice.prazoDiasUteis(), vertice.taxa(), taxaImportada);
            }
        }

        if (!algumPrazoComparado) {
            return new ResultadoTeste(identificador(), Classificacao.BLOQUEANTE, ResultadoValidacao.NAO_APLICAVEL, null, limite, "Nenhum prazo correspondente encontrado na curva importada");
        }

        if (violou) {
            return new ResultadoTeste(identificador(), Classificacao.BLOQUEANTE, ResultadoValidacao.REPROVADO, maiorVariacao, limite, detalhe);
        }

        return new ResultadoTeste(identificador(), Classificacao.BLOQUEANTE, ResultadoValidacao.APROVADO, maiorVariacao, limite, detalhe);
    }
}
