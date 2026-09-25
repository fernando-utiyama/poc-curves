package br.com.poc.domain.pipeline.normalizer;

import br.com.poc.domain.calendar.B3BusinessCalendar;
import br.com.poc.domain.calendar.BusinessCalendar;
import br.com.poc.domain.model.ConvencaoDias;
import br.com.poc.domain.model.TipoInsumoCurva;
import br.com.poc.domain.model.VerticeInsumo;
import br.com.poc.domain.pipeline.InsumoNormalizer;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

public class DefaultInsumoNormalizer implements InsumoNormalizer {

    private static final BigDecimal VALOR_NOMINAL_PADRAO = new BigDecimal("1000.0");
    private final BusinessCalendar defaultCalendar;

    public DefaultInsumoNormalizer() { this(new B3BusinessCalendar()); }

    public DefaultInsumoNormalizer(BusinessCalendar defaultCalendar) {
        this.defaultCalendar = Objects.requireNonNull(defaultCalendar, "defaultCalendar não pode ser nulo");
    }

    @Override
    public VerticeInsumo normalizar(VerticeInsumo insumo, TipoInsumoCurva tipoInsumo, ConvencaoDias convencao) {
        return normalizar(insumo, tipoInsumo, convencao, this.defaultCalendar);
    }

    @Override
    public VerticeInsumo normalizar(
        VerticeInsumo insumo,
        TipoInsumoCurva tipoInsumo,
        ConvencaoDias convencao,
        BusinessCalendar calendar
    ) {
        if (insumo == null) {
            return null;
        }

        BusinessCalendar cal = calendar != null ? calendar : this.defaultCalendar;

        // 1. Resolução e enriquecimento de prazos (dias úteis e dias corridos)
        Integer diasUteis = insumo.diasUteis();
        Integer diasCorridos = insumo.diasCorridos();

        if (insumo.dataBase() != null && insumo.dataVertice() != null) {
            if (diasUteis == null || diasUteis <= 0) {
                diasUteis = cal.contarDiasUteis(insumo.dataBase(), insumo.dataVertice());
            }
            if (diasCorridos == null || diasCorridos <= 0) {
                diasCorridos = (int) ChronoUnit.DAYS.between(insumo.dataBase(), insumo.dataVertice());
            }
        }

        int prazo = convencao.isDiasUteis()
            ? (diasUteis != null ? diasUteis : (diasCorridos != null ? diasCorridos : 0))
            : (diasCorridos != null ? diasCorridos : (diasUteis != null ? diasUteis : 0));

        TipoInsumoCurva tipo = tipoInsumo != null ? tipoInsumo : resolverTipoInsumo(insumo);

        // Se o prazo for inválido ou não houver tipo/dados a normalizar, retorna com prazos enriquecidos
        if (prazo <= 0 || tipo == null) {
            return new VerticeInsumo(
                insumo.ticker(),
                insumo.dataBase(),
                insumo.dataVertice(),
                diasUteis,
                diasCorridos,
                insumo.taxa(),
                insumo.valor(),
                insumo.flagMercado()
            );
        }

        BigDecimal taxaCalculada = insumo.taxa();
        BigDecimal fatorCalculado = insumo.valor();
        int base = convencao.getBaseAnual();

        switch (tipo) {
            case TAXA -> {
                if (taxaCalculada != null) {
                    double r = taxaCalculada.doubleValue();
                    double fatorDouble;
                    if (convencao.isDiasUteis()) {
                        fatorDouble = Math.pow(1.0 + r, (double) prazo / (double) base);
                    } else {
                        fatorDouble = 1.0 + (r * ((double) prazo / (double) base));
                    }
                    fatorCalculado = BigDecimal.valueOf(fatorDouble).setScale(16, RoundingMode.HALF_UP);
                    taxaCalculada = taxaCalculada.setScale(12, RoundingMode.HALF_UP);
                }
            }
            case FATOR -> {
                if (fatorCalculado != null) {
                    double f = fatorCalculado.doubleValue();
                    double taxaDouble;
                    if (convencao.isDiasUteis()) {
                        taxaDouble = Math.pow(f, (double) base / (double) prazo) - 1.0;
                    } else {
                        taxaDouble = (f - 1.0) * ((double) base / (double) prazo);
                    }
                    taxaCalculada = BigDecimal.valueOf(taxaDouble).setScale(12, RoundingMode.HALF_UP);
                    fatorCalculado = fatorCalculado.setScale(16, RoundingMode.HALF_UP);
                }
            }
            case PRECO_UNITARIO -> {
                if (insumo.valor() != null && insumo.valor().compareTo(BigDecimal.ZERO) > 0) {
                    double pu = insumo.valor().doubleValue();
                    double f = VALOR_NOMINAL_PADRAO.doubleValue() / pu;
                    double taxaDouble;
                    if (convencao.isDiasUteis()) {
                        taxaDouble = Math.pow(f, (double) base / (double) prazo) - 1.0;
                    } else {
                        taxaDouble = (f - 1.0) * ((double) base / (double) prazo);
                    }
                    taxaCalculada = BigDecimal.valueOf(taxaDouble).setScale(12, RoundingMode.HALF_UP);
                    fatorCalculado = BigDecimal.valueOf(f).setScale(16, RoundingMode.HALF_UP);
                }
            }
        }

        return new VerticeInsumo(
            insumo.ticker(),
            insumo.dataBase(),
            insumo.dataVertice(),
            diasUteis,
            diasCorridos,
            taxaCalculada,
            fatorCalculado,
            insumo.flagMercado()
        );
    }

    private TipoInsumoCurva resolverTipoInsumo(VerticeInsumo insumo) {
        if (insumo.taxa() != null) {
            return TipoInsumoCurva.TAXA;
        }
        if (insumo.valor() != null) {
            return TipoInsumoCurva.FATOR;
        }
        return null;
    }
}
