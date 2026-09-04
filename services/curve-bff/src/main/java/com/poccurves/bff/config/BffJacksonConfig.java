package com.poccurves.bff.config;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.module.SimpleModule;

import java.math.BigDecimal;

@Configuration
public class BffJacksonConfig {

    /**
     * Preserva todo BigDecimal como string de texto plano no JSON de saída para o navegador,
     * impedindo qualquer perda de precisão em double IEEE 754 de 12 casas decimais.
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
}
