package br.com.poc.domain.strategy;

import br.com.poc.domain.model.*;
import br.com.poc.domain.pipeline.InsumoNormalizer;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Construtor componível de curvas baseado em normalização de insumos e consolidação de vértices.
 */
public class ComposableCurveBuilder implements CurveBuilderStrategy {

    private final String strategyName;
    private final InsumoNormalizer normalizer;

    public ComposableCurveBuilder(String strategyName, InsumoNormalizer normalizer) {
        this.strategyName = Objects.requireNonNull(strategyName, "strategyName não pode ser nulo");
        this.normalizer = Objects.requireNonNull(normalizer, "normalizer não pode ser nulo");
    }

    @Override
    public String getStrategyName() { return strategyName; }


    @Override
    public List<VerticeCalculado> build(ContextoConstrucaoCurva contexto, List<VerticeInsumo> insumos) {
        if (insumos == null || insumos.size() < 2) {
            throw new IllegalArgumentException("Insumos insuficientes para construção da curva. Mínimo: 2.");
        }

        ConvencaoDias convencao = parseConvencao(contexto);
        int baseAnual = convencao.getBaseAnual();
        boolean usaDiasCorridos = !convencao.isDiasUteis();
        TipoInsumoCurva tipoInsumo = parseTipoInsumo(contexto);

        List<VerticeInsumo> normalizados = insumos.stream()
            .map(v -> normalizer.normalizar(v, tipoInsumo, convencao))
            .filter(v -> {
                Integer prazo = usaDiasCorridos ? v.diasCorridos() : v.diasUteis();
                return prazo != null && prazo > 0 && (v.taxa() != null || v.valor() != null);
            })
            .sorted(Comparator.comparingInt(v -> usaDiasCorridos ? v.diasCorridos() : v.diasUteis()))
            .toList();

        if (normalizados.size() < 2) {
            throw new IllegalArgumentException("Insumos válidos insuficientes após normalização.");
        }

        List<VerticeCalculado> result = new ArrayList<>();
        BigDecimal acumAnterior = BigDecimal.ONE;
        int prazoAnterior = 0;
        BigDecimal fatorDiarioUltimo = BigDecimal.ONE;

        for (VerticeInsumo v : normalizados) {
            int prazoAtual = usaDiasCorridos ? v.diasCorridos() : v.diasUteis();
            int deltaPrazo = prazoAtual - prazoAnterior;

            BigDecimal taxa = v.taxa() != null ? v.taxa() : BigDecimal.ZERO;
            double taxaDouble = taxa.doubleValue();

            double fatorAcumDouble;
            if (usaDiasCorridos) {
                fatorAcumDouble = 1.0 + (taxaDouble * ((double) prazoAtual / (double) baseAnual));
            } else {
                fatorAcumDouble = Math.pow(1.0 + taxaDouble, (double) prazoAtual / (double) baseAnual);
            }
            BigDecimal fatorAcum = BigDecimal.valueOf(fatorAcumDouble).setScale(16, RoundingMode.HALF_UP);

            double fatorPeriodoDouble = acumAnterior.compareTo(BigDecimal.ZERO) != 0
                ? fatorAcumDouble / acumAnterior.doubleValue()
                : 1.0;
            double fatorDiarioDouble = deltaPrazo > 0
                ? Math.pow(fatorPeriodoDouble, 1.0 / (double) deltaPrazo)
                : 1.0;
            BigDecimal fatorDiario = BigDecimal.valueOf(fatorDiarioDouble).setScale(12, RoundingMode.HALF_UP);

            result.add(new VerticeCalculado(
                contexto.ticker(),
                contexto.dataBase(),
                v.dataVertice(),
                v.diasUteis(),
                v.diasCorridos(),
                deltaPrazo,
                taxa.setScale(12, RoundingMode.HALF_UP),
                fatorDiario,
                fatorAcum
            ));

            acumAnterior = fatorAcum;
            prazoAnterior = prazoAtual;
            fatorDiarioUltimo = fatorDiario;
        }

        String anosParam = contexto.getParametro("HORIZONTE_MAX_ANOS", null);
        if (anosParam != null) {
            int anos = Integer.parseInt(anosParam);
            int prazoMax = anos * baseAnual;
            int ultimoPrazo = usaDiasCorridos
                ? normalizados.getLast().diasCorridos()
                : normalizados.getLast().diasUteis();

            if (ultimoPrazo < prazoMax) {
                int deltaExtrap = prazoMax - ultimoPrazo;
                double fatorExtrapoladoDouble = acumAnterior.doubleValue() * Math.pow(fatorDiarioUltimo.doubleValue(), deltaExtrap);
                BigDecimal fatorAcumExtrap = BigDecimal.valueOf(fatorExtrapoladoDouble).setScale(16, RoundingMode.HALF_UP);

                double taxaAnualizadaExtrapDouble;
                if (usaDiasCorridos) {
                    taxaAnualizadaExtrapDouble = (fatorExtrapoladoDouble - 1.0) * ((double) baseAnual / (double) prazoMax);
                } else {
                    taxaAnualizadaExtrapDouble = Math.pow(fatorExtrapoladoDouble, (double) baseAnual / (double) prazoMax) - 1.0;
                }
                BigDecimal taxaExtrap = BigDecimal.valueOf(taxaAnualizadaExtrapDouble).setScale(12, RoundingMode.HALF_UP);

                LocalDate dataVerticeExtrap = contexto.dataBase().plusDays((long) (prazoMax * (365.25 / (double) baseAnual)));
                int dcExtrap = (int) (prazoMax * (365.0 / (double) baseAnual));

                result.add(new VerticeCalculado(
                    contexto.ticker(),
                    contexto.dataBase(),
                    dataVerticeExtrap,
                    usaDiasCorridos ? null : prazoMax,
                    dcExtrap,
                    deltaExtrap,
                    taxaExtrap,
                    fatorDiarioUltimo,
                    fatorAcumExtrap
                ));
            }
        }

        return result;
    }

    private ConvencaoDias parseConvencao(ContextoConstrucaoCurva contexto) {
        String convStr = contexto.getParametro("CONVENCAO", "");
        if (convStr.contains("360")) {
            return ConvencaoDias.ACT_360;
        }
        if (convStr.contains("365")) {
            return ConvencaoDias.ACT_365;
        }
        return ConvencaoDias.DU_252;
    }

    private TipoInsumoCurva parseTipoInsumo(ContextoConstrucaoCurva contexto) {
        String modDado = contexto.getParametro("MOD_DADO", null);
        if (modDado == null || modDado.isBlank()) {
            modDado = contexto.getParametro("cModDado", "TAXA");
        }
        try {
            return TipoInsumoCurva.valueOf(modDado.toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return TipoInsumoCurva.TAXA;
        }
    }
}
