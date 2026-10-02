package br.com.poc.adapter.in.api.rest.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuração do OpenAPI/Swagger da aplicação.
 */
@Configuration
public class SwaggerConfiguration {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("Orquestrador de Curvas")
                .description("Agendamento e execução de tarefas do TCEN")
                .version("v1"));
    }
}
