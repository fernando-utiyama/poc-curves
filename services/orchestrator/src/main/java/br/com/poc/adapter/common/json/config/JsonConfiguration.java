package br.com.poc.adapter.common.json.config;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.DeserializationFeature;

/**
 * Configuração do Jackson para toda a aplicação: ausência de campo em vez de
 * erro para propriedade desconhecida na desserialização. Datas de `java.time`
 * já saem em ISO-8601 por padrão no Jackson 3, sem precisar de configuração.
 */
@Configuration
public class JsonConfiguration {

    @Bean
    public JsonMapperBuilderCustomizer jsonMapperBuilderCustomizer() {
        return builder -> builder.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }
}
