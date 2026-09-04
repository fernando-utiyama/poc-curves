package com.poccurves.orchestrator.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.CustomizableThreadFactory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
public class BackfillConfig {

    @Value("${backfill.dispatcher-threads:8}")
    private int dispatcherThreads;

    @Bean
    public ExecutorService backfillExecutor() {
        return Executors.newFixedThreadPool(
                dispatcherThreads,
                new CustomizableThreadFactory("backfill-")
        );
    }
}
