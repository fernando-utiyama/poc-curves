package com.poccurves.processor.config;

import com.poccurves.processor.application.*;
import com.poccurves.processor.domain.BateriaValidacaoCarga;
import com.poccurves.processor.domain.DatasetParserRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Único ponto de wiring dos casos de uso e classes puras em `domain`/`application` que precisam
 * virar bean — nenhuma delas tem anotação Spring (guarda de arquitetura hexagonal, ver
 * openspec/changes/hexagonal-architecture).
 */
@Configuration
public class UseCaseConfig {

    @Bean
    public BateriaValidacaoCarga bateriaValidacaoCarga() {
        return new BateriaValidacaoCarga();
    }

    @Bean
    public MetricasIngestao metricasIngestao(MeterRegistry meterRegistry) {
        return new MetricasIngestao(meterRegistry);
    }

    @Bean
    public IngestaoService ingestaoService(
            LoteIngestaoRepositoryPort loteIngestaoRepositoryPort,
            PontoDadoMercadoRepositoryPort pontoDadoMercadoRepositoryPort,
            ExecucaoCurvaLeituraRepositoryPort execucaoCurvaLeituraRepositoryPort
    ) {
        return new IngestaoService(loteIngestaoRepositoryPort, pontoDadoMercadoRepositoryPort, execucaoCurvaLeituraRepositoryPort);
    }

    @Bean
    public PublicacaoCurvaService publicacaoCurvaService(
            DefinicaoCurvaLeituraRepositoryPort definicaoCurvaLeituraRepositoryPort,
            VersaoCurvaRepositoryPort versaoCurvaRepositoryPort,
            VerticeCurvaRepositoryPort verticeCurvaRepositoryPort,
            ProcedenciaCurvaRepositoryPort procedenciaCurvaRepositoryPort,
            ValidacaoCurvaRepositoryPort validacaoCurvaRepositoryPort,
            BateriaValidacaoCarga bateriaValidacaoCarga
    ) {
        return new PublicacaoCurvaService(
                definicaoCurvaLeituraRepositoryPort, versaoCurvaRepositoryPort, verticeCurvaRepositoryPort,
                procedenciaCurvaRepositoryPort, validacaoCurvaRepositoryPort, bateriaValidacaoCarga);
    }

    @Bean
    public ProcessarEnvelopeIngestaoUseCase processarEnvelopeIngestaoUseCase(
            DatasetParserRegistry datasetParserRegistry,
            IngestaoService ingestaoService,
            MetricasIngestao metricasIngestao,
            ExecucaoCurvaLeituraRepositoryPort execucaoCurvaLeituraRepositoryPort,
            PontoDadoMercadoRepositoryPort pontoDadoMercadoRepositoryPort,
            PublicacaoCurvaService publicacaoCurvaService,
            NormalizedEventPort normalizedEventPort
    ) {
        return new ProcessarEnvelopeIngestaoUseCase(
                datasetParserRegistry, ingestaoService, metricasIngestao, execucaoCurvaLeituraRepositoryPort,
                pontoDadoMercadoRepositoryPort, publicacaoCurvaService, normalizedEventPort);
    }
}
