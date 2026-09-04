package com.poccurves.engine.config;
import com.poccurves.engine.domain.interpolacao.InterpoladorFlatForward;
import com.poccurves.engine.domain.interpolacao.InterpoladorLinear;
import com.poccurves.engine.domain.interpolacao.InterpoladorLogCubico;
import com.poccurves.engine.domain.interpolacao.InterpoladorLogLinear;
import com.poccurves.engine.domain.interpolacao.InterpoladorMonotonicoConvexo;
import com.poccurves.engine.domain.interpolacao.InterpoladorRegistry;
import com.poccurves.engine.domain.interpolacao.InterpoladorSplineCubicoNatural;
import com.poccurves.engine.domain.interpolacao.PoliticaExtrapolacaoEstrita;
import com.poccurves.engine.domain.interpolacao.PoliticaExtrapolacaoForwardConstante;
import com.poccurves.engine.domain.interpolacao.PoliticaExtrapolacaoForwardLinear;
import com.poccurves.engine.domain.interpolacao.PoliticaExtrapolacaoRegistry;
import com.poccurves.engine.domain.interpolacao.PoliticaExtrapolacaoTaxaConstante;

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
