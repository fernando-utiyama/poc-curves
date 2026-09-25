package br.com.poc.domain.service;

import br.com.poc.domain.model.MetodoInterpolacao;
import br.com.poc.domain.model.PoliticaExtrapolacao;
import br.com.poc.domain.model.PontoInterpolado;
import br.com.poc.domain.model.VerticeCalculado;
import br.com.poc.domain.pipeline.CurveExtrapolator;
import br.com.poc.domain.pipeline.CurveExtrapolatorRegistry;
import br.com.poc.domain.pipeline.CurveInterpolator;
import br.com.poc.domain.pipeline.CurveInterpolatorRegistry;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Orquestrador de domínio puro para interpolação e extrapolação analítica de curvas.
 *
 * <p>Conforme os princípios da Arquitetura Hexagonal:
 * <ul>
 *  <li><b>Isolamento de Frameworks:</b> Não possui anotações de Spring ou persistência.</li>
 *  <li><b>Inversão de Dependências (DIP) e Open/Closed (OCP):</b> Os algoritmos são resolvidos
 *      via {@link CurveInterpolatorRegistry} e {@link CurveExtrapolatorRegistry}, permitindo estender
 *      novas técnicas sem modificar o orquestrador.</li>
 *  <li><b>Single Responsibility (SRP):</b> Orquestra o fluxo de dados, ordenação e conversão de
 *      fatores, delegando a matemática a cada Strategy.</li>
 * </ul>
 * </p>
 */
public class CurvaInterpolacaoDomainService {

    private final CurveInterpolatorRegistry interpolatorRegistry;
    private final CurveExtrapolatorRegistry extrapolatorRegistry;

    public CurvaInterpolacaoDomainService() { this(new CurveInterpolatorRegistry(), new CurveExtrapolatorRegistry()); }

    public CurvaInterpolacaoDomainService(
        CurveInterpolatorRegistry interpolatorRegistry,
        CurveExtrapolatorRegistry extrapolatorRegistry
    ) {
        this.interpolatorRegistry = Objects.requireNonNull(interpolatorRegistry, "interpolatorRegistry não pode ser nulo");
        this.extrapolatorRegistry = Objects.requireNonNull(extrapolatorRegistry, "extrapolatorRegistry não pode ser nulo");
    }

    public List<PontoInterpolado> interpolar(
        List<VerticeCalculado> vertices,
        List<Integer> prazosAlvo,
        int baseAnual,
        MetodoInterpolacao metodo,
        PoliticaExtrapolacao politica
    ) {
        if (vertices == null || vertices.size() < 2) {
            throw new IllegalArgumentException("Mínimo de 2 vértices para interpolação.");
        }
        if (prazosAlvo == null || prazosAlvo.isEmpty()) {
            return List.of();
        }

        List<VerticeCalculado> sorted = vertices.stream()
            .filter(v -> v.diasUteis() != null && v.taxaAnualizada() != null)
            .sorted(Comparator.comparingInt(VerticeCalculado::diasUteis))
            .toList();

        if (sorted.size() < 2) {
            throw new IllegalArgumentException("Vértices válidos insuficientes.");
        }

        int minPrazo = sorted.getFirst().diasUteis();
        int maxPrazo = sorted.getLast().diasUteis();

        // Validação da política STRICT antecipada: se qualquer prazo for fora do domínio, rejeita a requisição integral
        if (politica == PoliticaExtrapolacao.STRICT) {
            boolean outOfDomain = prazosAlvo.stream().anyMatch(p -> p < minPrazo || p > maxPrazo);
            if (outOfDomain) {
                throw new IllegalStateException("CURV-OUT-OF-DOMAIN: Prazo fora do intervalo [" + minPrazo + ", " + maxPrazo + "] sob política STRICT.");
            }
        }

        // Nós de interpolação
        double[] x = sorted.stream().mapToDouble(v -> (double) v.diasUteis()).toArray();
        double[] yTaxa = sorted.stream().mapToDouble(v -> v.taxaAnualizada().doubleValue()).toArray();

        CurveInterpolator interpolator = interpolatorRegistry.resolve(metodo);
        CurveExtrapolator extrapolator = extrapolatorRegistry.resolve(politica);

        List<PontoInterpolado> resultado = new ArrayList<>(prazosAlvo.size());

        for (int p : prazosAlvo) {
            boolean extrapolado = (p < minPrazo || p > maxPrazo);
            BigDecimal taxaCalculada;

            if (extrapolado) {
                CurveExtrapolator.Direcao direcao = p < minPrazo
                    ? CurveExtrapolator.Direcao.INFERIOR
                    : CurveExtrapolator.Direcao.SUPERIOR;
                taxaCalculada = extrapolator.extrapolar(p, x, yTaxa, direcao, baseAnual);
            } else {
                taxaCalculada = interpolator.interpolar(p, x, yTaxa);
            }

            // Fator acumulado implícito sob convenção exponencial
            double taxaDouble = taxaCalculada.doubleValue();
            double fatorAcumDouble = Math.pow(1.0 + taxaDouble, (double) p / (double) baseAnual);
            BigDecimal fatorAcum = BigDecimal.valueOf(fatorAcumDouble).setScale(16, RoundingMode.HALF_UP);

            resultado.add(new PontoInterpolado(
                p,
                taxaCalculada.setScale(12, RoundingMode.HALF_UP),
                fatorAcum,
                extrapolado
            ));
        }

        return resultado;
    }
}
