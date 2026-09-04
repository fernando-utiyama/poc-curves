package com.poccurves.processor.domain.parsing;

import java.time.LocalDate;

/**
 * Contrato de um parser de dataset específico (ex.: arquivo de Preços de
 * Referência, BVBG.086, BVBG.028). O processor roteia pelo campo `dataset`
 * do envelope de evento e delega ao parser registrado para aquele dataset.
 */
public interface DatasetParser {

    /** Identificador do dataset que este parser trata (ex.: "PR_DI1", "BVBG.086", "BVBG.028"). */
    String dataset();

    /**
     * Interpreta o conteúdo bruto de um bloco e produz os pontos de dado de mercado, ou uma
     * falha nomeada. {@code referenceDate} é a data de referência do envelope — parsers cujo
     * conteúdo já traz a data embutida (ex. BVBG.086/BVBG.028, campo TradDt) podem ignorá-la;
     * parsers cujo conteúdo não traz data nenhuma (ex. curva pronta da B3, um CSV de vértices
     * sem coluna de data) dependem dela.
     */
    ParseResult parse(byte[] conteudo, String encoding, LocalDate referenceDate);
}
