package com.poccurves.processor.config;
import com.poccurves.processor.application.model.B3CurvaProntaParser;
import com.poccurves.processor.application.model.Bvbg028CadastroParser;
import com.poccurves.processor.application.model.Bvbg086PricRptParser;
import com.poccurves.processor.application.model.DatasetParser;
import com.poccurves.processor.application.model.DatasetParserRegistry;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * Registra os DatasetParser conhecidos no {@link DatasetParserRegistry}. Os datasets do
 * TaxaSwap.txt (PRE/DCL/PTX/INP/DPL, openspec/changes/legado-schema-curvas-mercado) não têm
 * entrada aqui — {@code ProcessarEnvelopeIngestaoUseCase} intercepta esses datasets antes de
 * chegar neste registro genérico e grava direto em {@code tBtrsCurvaPrimr}, via
 * {@link com.poccurves.processor.application.model.B3TaxaSwapParser#extrairVertices}.
 */
@Configuration
public class ParserConfig {

    @Bean
    public DatasetParserRegistry datasetParserRegistry() {
        List<DatasetParser> parsers = new ArrayList<>();
        for (String dataset : Bvbg086PricRptParser.datasetsCobertos()) {
            parsers.add(new Bvbg086PricRptParser(dataset));
        }
        parsers.add(new Bvbg028CadastroParser());
        parsers.add(new B3CurvaProntaParser("B3_CURVA_PRE"));
        return new DatasetParserRegistry(parsers);
    }
}
