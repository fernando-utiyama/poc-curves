package com.poccurves.engine.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@Configuration
public class AsyncConfig {

    @Bean(name = "construcaoCurvaExecutor", destroyMethod = "shutdown")
    public Executor construcaoCurvaExecutor() {
        return Executors.newFixedThreadPool(4);
    }
}
