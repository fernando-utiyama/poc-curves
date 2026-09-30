package br.com.poc.infrastructure.config;

import br.com.poc.application.port.in.usecase.ProvedorUseCase;
import br.com.poc.application.service.ProvedorUseCaseImpl;
import br.com.poc.application.port.out.ProvedorRepositoryPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BeanConfig {

    @Bean
    public ProvedorUseCase provedorUseCase(ProvedorRepositoryPort repositoryPort) {
        return new ProvedorUseCaseImpl(repositoryPort);
    }
}
