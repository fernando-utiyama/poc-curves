package com.poccurves.processor.config;
import com.poccurves.processor.application.model.B3CurvaProntaParser;
import com.poccurves.processor.application.model.DatasetParserRegistry;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Registra os DatasetParser conhecidos no {@link DatasetParserRegistry}. Os datasets do
 * TaxaSwap.txt (PRE/DCL/PTX/INP/DPL, openspec/changes/legado-schema-curvas-mercado) não têm
 * entrada aqui — {@code ProcessarEnvelopeIngestaoUseCase} intercepta esses datasets antes de
 * chegar neste registro genérico e grava direto em {@code tBtrsCurvaPrimr}, via
 * {@link com.poccurves.processor.application.model.B3TaxaSwapParser#extrairVertices}.
 * <p>
 * Os parsers de instrumento bruto BVBG.086/BVBG.028 (DI1/BOOTSTRAPPED) foram removidos —
 * a plataforma manteve apenas as 5 curvas TS B3 (PRE/DCL/PTX/INP/DPL) e a curva pronta
 * B3_CURVA_PRE como curvas reais de produção.
 */
@Configuration
public class ParserConfig {

    @Bean
    public DatasetParserRegistry datasetParserRegistry() {
        return new DatasetParserRegistry(List.of(new B3CurvaProntaParser("B3_CURVA_PRE")));
    }
}
