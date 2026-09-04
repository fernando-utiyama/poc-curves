package com.poccurves.processor.config;

import com.poccurves.processor.domain.B3CurvaProntaParser;
import com.poccurves.processor.domain.Bvbg028CadastroParser;
import com.poccurves.processor.domain.Bvbg086PricRptParser;
import com.poccurves.processor.domain.DatasetParser;
import com.poccurves.processor.domain.DatasetParserRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/** Registra os DatasetParser conhecidos no {@link DatasetParserRegistry}. */
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
