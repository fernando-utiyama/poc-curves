package com.poccurves.engine.domain.construcao;
import com.poccurves.engine.domain.curva.CurvaJuros;
import com.poccurves.engine.domain.curva.Vertice;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Ferramenta genérica de comparação entre duas curvas de juros.
 * <p>
 * Funciona e é testável com curvas sintéticas.
 * Bloqueio real (fato 7): atualmente não possui um teste com dado real da B3 
 * porque o dataset real de "taxas de referência"/"curva pronta" da B3 ainda 
 * não existe no sistema.
 */
public final class ReconciliadorCurva {

    public record DiscrepanciaReconciliacao(
            int prazoDiasUteis, 
            BigDecimal taxaConstruida, 
            BigDecimal taxaReferencia, 
            BigDecimal diferencaAbsoluta
    ) {}

    /**
     * Reconcilia duas curvas, apontando diferenças que ultrapassam a tolerância.
     * <p>
     * Prazos presentes apenas em uma das curvas são ignorados e não geram discrepância,
     * pois a reconciliação exige contraparte e não é o objetivo desta ferramenta decidir
     * o que fazer com prazo sem contraparte (isso é responsabilidade de quem chama).
     * 
     * @param construida curva construída
     * @param referencia curva de referência
     * @param toleranciaAbsoluta tolerância absoluta máxima permitida
     * @return lista de discrepâncias encontradas
     */
    public List<DiscrepanciaReconciliacao> reconciliar(CurvaJuros construida, CurvaJuros referencia, BigDecimal toleranciaAbsoluta) {
        Objects.requireNonNull(construida, "construida não pode ser nula");
        Objects.requireNonNull(referencia, "referencia não pode ser nula");
        Objects.requireNonNull(toleranciaAbsoluta, "toleranciaAbsoluta não pode ser nula");
        
        if (toleranciaAbsoluta.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("toleranciaAbsoluta não pode ser negativa");
        }

        List<DiscrepanciaReconciliacao> discrepancias = new ArrayList<>();
        
        for (Vertice vConstruida : construida.vertices()) {
            int prazo = vConstruida.prazoDiasUteis();
            
            boolean existeReferencia = referencia.vertices().stream()
                    .anyMatch(v -> v.prazoDiasUteis() == prazo);
                    
            if (existeReferencia) {
                BigDecimal taxaConstruida = vConstruida.taxa();
                BigDecimal taxaReferencia = referencia.taxaEm(prazo);
                BigDecimal diferenca = taxaConstruida.subtract(taxaReferencia).abs();
                
                if (diferenca.compareTo(toleranciaAbsoluta) > 0) {
                    discrepancias.add(new DiscrepanciaReconciliacao(prazo, taxaConstruida, taxaReferencia, diferenca));
                }
            }
        }
        
        return discrepancias;
    }
}
