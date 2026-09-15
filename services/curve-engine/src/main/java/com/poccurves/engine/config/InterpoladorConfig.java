package com.poccurves.engine.config;
import com.poccurves.engine.application.construcao.InterpoladorFlatForward;
import com.poccurves.engine.application.construcao.InterpoladorLinear;
import com.poccurves.engine.application.construcao.InterpoladorLogCubico;
import com.poccurves.engine.application.construcao.InterpoladorLogLinear;
import com.poccurves.engine.application.construcao.InterpoladorMonotonicoConvexo;
import com.poccurves.engine.application.construcao.InterpoladorRegistry;
import com.poccurves.engine.application.construcao.InterpoladorSplineCubicoNatural;
import com.poccurves.engine.application.construcao.PoliticaExtrapolacaoEstrita;
import com.poccurves.engine.application.construcao.PoliticaExtrapolacaoForwardConstante;
import com.poccurves.engine.application.construcao.PoliticaExtrapolacaoForwardLinear;
import com.poccurves.engine.application.construcao.PoliticaExtrapolacaoRegistry;
import com.poccurves.engine.application.construcao.PoliticaExtrapolacaoTaxaConstante;

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
