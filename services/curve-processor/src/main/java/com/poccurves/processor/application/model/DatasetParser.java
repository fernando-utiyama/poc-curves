package com.poccurves.processor.application.model;

import java.time.LocalDate;

/**
 * Contrato de um parser de dataset específico (ex.: {@link B3CurvaProntaParser}, curva pronta
 * da B3, ou {@link B3TaxaSwapParser}, arquivo TaxaSwap.txt). O processor roteia pelo campo
 * `dataset` do envelope de evento e delega ao parser registrado para aquele dataset.
 */
public interface DatasetParser {

    /** Identificador do dataset que este parser trata (ex.: "B3_CURVA_PRE"). */
    String dataset();

    /**
     * Interpreta o conteúdo bruto de um bloco e produz os pontos de dado de mercado, ou uma
     * falha nomeada. {@code referenceDate} é a data de referência do envelope — parsers cujo
     * conteúdo já traz a data embutida podem ignorá-la; parsers cujo conteúdo não traz data
     * nenhuma (ex. curva pronta da B3, um CSV de vértices sem coluna de data) dependem dela.
     */
    ParseResult parse(byte[] conteudo, String encoding, LocalDate referenceDate);
}
