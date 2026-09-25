package br.com.poc.domain.model;

public enum ConvencaoDias {
    DU_252(252, true, "Dias Úteis / Base 252"),
    ACT_360(360, false, "Dias Corridos / Base 360"),
    ACT_365(365, false, "Dias Corridos / Base 365"),
    BASE_30_360(360, false, "Comercial 30/360");

    private final int baseAnual;
    private final boolean diasUteis;
    private final String descricao;

    ConvencaoDias(int baseAnual, boolean diasUteis, String descricao) {
        this.baseAnual = baseAnual;
        this.diasUteis = diasUteis;
        this.descricao = descricao;
    }

    public int getBaseAnual() { return baseAnual; }

    public boolean isDiasUteis() { return diasUteis; }

    public String getDescricao() { return descricao; }

    public static ConvencaoDias fromBaseEModo(Integer base, Boolean usaDiasUteis) {
        if (Boolean.TRUE.equals(usaDiasUteis)) {
            return DU_252;
        }
        if (base != null && base == 365) {
            return ACT_365;
        }
        return ACT_360;
    }
}
