package com.poccurves.api.config;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.module.SimpleModule;

import java.math.BigDecimal;

@Configuration
public class AppConfig {

    /**
     * Garante que todo BigDecimal seja serializado como texto plano no JSON,
     * impedindo perda de precisão de 12 casas em clientes JavaScript.
     */
    @Bean
    public JsonMapperBuilderCustomizer jsonCustomizer() {
        return builder -> {
            SimpleModule bigDecimalModule = new SimpleModule();
            bigDecimalModule.addSerializer(BigDecimal.class, new ValueSerializer<BigDecimal>() {
                @Override
                public void serialize(BigDecimal value, JsonGenerator gen, SerializationContext ctxt) {
                    if (value == null) {
                        gen.writeNull();
                    } else {
                        gen.writeString(value.toPlainString());
                    }
                }
            });
            builder.addModule(bigDecimalModule);
            builder.disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS);
        };
    }

    @Bean
    public RestClient engineRestClient(org.springframework.boot.restclient.RestClientCustomizer customizer) {
        return RestClient.builder()
                .baseUrl("http://localhost:8083")
                .build();
    }
}
