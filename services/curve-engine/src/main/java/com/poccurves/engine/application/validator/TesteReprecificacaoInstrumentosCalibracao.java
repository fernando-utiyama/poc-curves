package com.poccurves.engine.application.validator;
import com.poccurves.engine.application.model.Classificacao;
import com.poccurves.engine.application.model.ContextoValidacao;
import com.poccurves.engine.application.model.ResultadoTeste;
import com.poccurves.engine.application.model.ResultadoValidacao;


import java.math.BigDecimal;

/**
 * Compara a taxa gerada pela curva nos prazos dos instrumentos originais (insumos de calibração)
 * com as próprias taxas de mercado fornecidas, garantindo que o modelo reprecifica seus insumos corretamente.
 * Para o modelo embutido PRE_DI1_B3 (montagem direta, sem stripping), esta diferença é sempre zero
 * por construção (o vértice é o próprio insumo). O teste ganha poder de detecção real em modelos
 * com stripping real, mas a infraestrutura está pronta.
 * A medidaObservada reporta a maior diferença absoluta encontrada entre taxa calculada e taxa de mercado.
 */
public class TesteReprecificacaoInstrumentosCalibracao implements TesteValidacao {

    @Override
    public String identificador() {
        return "REPRECIFICACAO_INSTRUMENTOS_CALIBRACAO";
    }

    @Override
    public ResultadoTeste executar(ContextoValidacao contexto, BigDecimal limite) {
        var insumos = contexto.insumosOriginais();
        if (insumos == null || insumos.isEmpty()) {
            return new ResultadoTeste(identificador(), Classificacao.BLOQUEANTE, ResultadoValidacao.NAO_APLICAVEL, null, limite, "Sem insumos originais para reprecificar");
        }

        BigDecimal maiorDiferenca = BigDecimal.ZERO;
        boolean violou = false;
        String detalhe = "Reprecificação dos instrumentos dentro da tolerância";
        boolean algumInsumoComparado = false;

        for (var insumo : insumos) {
            if (insumo.taxaAjuste() == null || insumo.diasUteisVencimento() == null) {
                continue;
            }

            BigDecimal taxaCurva;
            try {
                taxaCurva = contexto.curvaConstruida().taxaEm(insumo.diasUteisVencimento());
            } catch (IllegalArgumentException e) {
                // Prazo do insumo não virou vértice, pula
                continue;
            }

            algumInsumoComparado = true;
            BigDecimal diferenca = taxaCurva.subtract(insumo.taxaAjuste()).abs();

            if (diferenca.compareTo(maiorDiferenca) > 0) {
                maiorDiferenca = diferenca;
            }

            if (!violou && diferenca.compareTo(limite) > 0) {
                violou = true;
                detalhe = String.format("Diferença de reprecificação de %s no prazo %d (taxa curva: %s, taxa mercado: %s) excede o limite", 
                    diferenca, insumo.diasUteisVencimento(), taxaCurva, insumo.taxaAjuste());
            }
        }

        if (!algumInsumoComparado) {
             return new ResultadoTeste(identificador(), Classificacao.BLOQUEANTE, ResultadoValidacao.NAO_APLICAVEL, null, limite, "Nenhum insumo possui dados completos ou corresponde a um vértice da curva");
        }

        if (violou) {
            return new ResultadoTeste(identificador(), Classificacao.BLOQUEANTE, ResultadoValidacao.REPROVADO, maiorDiferenca, limite, detalhe);
        }

        return new ResultadoTeste(identificador(), Classificacao.BLOQUEANTE, ResultadoValidacao.APROVADO, maiorDiferenca, limite, detalhe);
    }
}
