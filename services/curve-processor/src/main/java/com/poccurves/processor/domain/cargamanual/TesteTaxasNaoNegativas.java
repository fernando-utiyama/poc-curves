package com.poccurves.processor.domain.cargamanual;
import com.poccurves.processor.domain.curva.VerticeCurva;


import java.math.BigDecimal;
import java.util.List;

/**
 * Bloqueante: nenhuma taxa de juros carregada manualmente pode ser
 * negativa — sinal quase sempre de erro de digitação/formato na carga
 * (ex.: separador decimal errado inflando a ordem de grandeza), não de
 * mercado real neste contexto de POC.
 */
public final class TesteTaxasNaoNegativas implements TesteValidacaoCarga {

    @Override
    public String identificador() {
        return "TAXAS_NAO_NEGATIVAS";
    }

    @Override
    public ResultadoTesteCarga executar(List<VerticeCurva> vertices) {
        for (VerticeCurva vertice : vertices) {
            if (vertice.taxa().compareTo(BigDecimal.ZERO) < 0) {
                return new ResultadoTesteCarga(
                        identificador(),
                        Classificacao.BLOQUEANTE,
                        ResultadoValidacaoCarga.REPROVADO,
                        vertice.taxa(),
                        BigDecimal.ZERO,
                        "taxa negativa no vértice de prazo " + vertice.prazoDiasUteis() + ": " + vertice.taxa());
            }
        }
        return new ResultadoTesteCarga(
                identificador(),
                Classificacao.BLOQUEANTE,
                ResultadoValidacaoCarga.APROVADO,
                null,
                BigDecimal.ZERO,
                null);
    }
}
