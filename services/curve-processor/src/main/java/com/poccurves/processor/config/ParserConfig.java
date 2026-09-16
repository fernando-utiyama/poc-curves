package com.poccurves.processor.config;
import com.poccurves.processor.application.model.B3CurvaProntaParser;
import com.poccurves.processor.application.model.B3TaxaSwapParser;
import com.poccurves.processor.application.model.Bvbg028CadastroParser;
import com.poccurves.processor.application.model.Bvbg086PricRptParser;
import com.poccurves.processor.application.model.DatasetParser;
import com.poccurves.processor.application.model.DatasetParserRegistry;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Registra os DatasetParser conhecidos no {@link DatasetParserRegistry}. */
@Configuration
public class ParserConfig {

    /**
     * Códigos de curva do TaxaSwap.txt (openspec/changes/b3-additional-curves) cobertos por este
     * serviço, por dataset. Registro histórico do caminho genérico original — desde
     * openspec/changes/legado-schema-curvas-mercado, os 5 datasets (PRE incluído, ver
     * ProcessarEnvelopeIngestaoUseCase.DATASETS_TAXA_SWAP_SCHEMA_LEGADO) são interceptados por
     * {@code processarTaxaSwapSchemaLegado} ANTES de chegar neste registro — as 5 instâncias de
     * {@link B3TaxaSwapParser} abaixo ficam registradas mas inalcançáveis na prática. Não
     * removidas por ora (mesma decisão de manter o OraculoTaxaSwapValidator intacto).
     */
    private static final Map<String, String> DATASETS_TAXA_SWAP = Map.of(
            "B3_TAXA_SWAP_PRE", "PRE",
            "B3_TAXA_SWAP_DCL", "DCL",
            "B3_TAXA_SWAP_PTX", "PTX",
            "B3_TAXA_SWAP_INP", "INP",
            "B3_TAXA_SWAP_DPL", "DPL"
    );

    @Bean
    public DatasetParserRegistry datasetParserRegistry() {
        List<DatasetParser> parsers = new ArrayList<>();
        for (String dataset : Bvbg086PricRptParser.datasetsCobertos()) {
            parsers.add(new Bvbg086PricRptParser(dataset));
        }
        parsers.add(new Bvbg028CadastroParser());
        parsers.add(new B3CurvaProntaParser("B3_CURVA_PRE"));
        DATASETS_TAXA_SWAP.forEach((dataset, codigoCurva) ->
                parsers.add(new B3TaxaSwapParser(dataset, codigoCurva)));
        return new DatasetParserRegistry(parsers);
    }
}
