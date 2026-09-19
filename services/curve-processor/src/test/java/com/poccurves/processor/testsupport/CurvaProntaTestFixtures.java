package com.poccurves.processor.testsupport;

/** Constrói linhas no formato B3 de curva pronta (Descrição;Dias Úteis;Dias Corridos;Preço/Taxa). */
public final class CurvaProntaTestFixtures {

    private CurvaProntaTestFixtures() {
    }

    /** Linha com a mesma chave de instrumento repetida nas colunas de dias úteis/corridos — suficiente para testes de infraestrutura genérica de pipeline (não de parsing). */
    public static String linha(String chaveInstrumento) {
        return linha(chaveInstrumento, chaveInstrumento, "10,000");
    }

    /** Linha com valores reais e distintos de dias úteis/corridos/taxa. */
    public static String linha(String diasUteis, String diasCorridos, String taxa) {
        return "DI x pré;" + diasUteis + ";" + diasCorridos + ";" + taxa;
    }
}
