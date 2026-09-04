package com.poccurves.engine.config;
import com.poccurves.engine.application.model.InterpoladorFlatForward;
import com.poccurves.engine.application.model.InterpoladorLinear;
import com.poccurves.engine.application.model.InterpoladorLogCubico;
import com.poccurves.engine.application.model.InterpoladorLogLinear;
import com.poccurves.engine.application.model.InterpoladorMonotonicoConvexo;
import com.poccurves.engine.application.model.InterpoladorRegistry;
import com.poccurves.engine.application.model.InterpoladorSplineCubicoNatural;
import com.poccurves.engine.application.model.PoliticaExtrapolacaoEstrita;
import com.poccurves.engine.application.model.PoliticaExtrapolacaoForwardConstante;
import com.poccurves.engine.application.model.PoliticaExtrapolacaoForwardLinear;
import com.poccurves.engine.application.model.PoliticaExtrapolacaoRegistry;
import com.poccurves.engine.application.model.PoliticaExtrapolacaoTaxaConstante;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.List;

@Configuration
public class InterpoladorConfig {

    @Bean
    public InterpoladorRegistry interpoladorRegistry() {
        return new InterpoladorRegistry(List.of(
                new InterpoladorLinear(),
                new InterpoladorFlatForward(),
                new InterpoladorLogLinear(),
                new InterpoladorLogCubico(),
                new InterpoladorSplineCubicoNatural(),
                new InterpoladorMonotonicoConvexo()
        ));
    }

    @Bean
    public PoliticaExtrapolacaoRegistry politicaExtrapolacaoRegistry() {
        return new PoliticaExtrapolacaoRegistry(List.of(
                new PoliticaExtrapolacaoEstrita(),
                new PoliticaExtrapolacaoTaxaConstante(),
                new PoliticaExtrapolacaoForwardConstante(),
                new PoliticaExtrapolacaoForwardLinear()
        ));
    }
}
