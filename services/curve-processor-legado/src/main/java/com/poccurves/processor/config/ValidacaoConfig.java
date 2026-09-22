package com.poccurves.processor.config;

import com.poccurves.common.event.EventEnvelopeSchemaValidator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * {@code common} é deliberadamente livre de framework de aplicação (ver
 * services/common/pom.xml) — EventEnvelopeSchemaValidator não é anotado
 * como bean Spring, então cada serviço que o usa precisa registrá-lo aqui.
 */
@Configuration
public class ValidacaoConfig {

    @Bean
    public EventEnvelopeSchemaValidator eventEnvelopeSchemaValidator() {
        return new EventEnvelopeSchemaValidator();
    }
}
