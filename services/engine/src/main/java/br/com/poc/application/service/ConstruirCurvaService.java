package br.com.poc.application.service;

import br.com.poc.application.exception.BusinessException;
import br.com.poc.application.exception.NotFoundException;
import br.com.poc.application.port.in.ConstruirCurvaUseCase;
import br.com.poc.application.port.out.CurvaPersistencePort;
import br.com.poc.domain.model.ContextoConstrucaoCurva;
import br.com.poc.domain.model.VerticeCalculado;
import br.com.poc.domain.model.VerticeInsumo;
import br.com.poc.domain.pipeline.CurveInterpolator;
import br.com.poc.domain.strategy.CurveBuilderRegistry;
import br.com.poc.domain.strategy.CurveBuilderStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
public class ConstruirCurvaService implements ConstruirCurvaUseCase {

    private static final Logger log = LoggerFactory.getLogger(ConstruirCurvaService.class);

    private final CurvaPersistencePort persistencePort;
    private final CurveBuilderRegistry registry;
    private final ApplicationContext applicationContext;

    public ConstruirCurvaService(
        CurvaPersistencePort persistencePort,
        CurveBuilderRegistry registry,
        ApplicationContext applicationContext
    ) {
        this.persistencePort = persistencePort;
        this.registry = registry;
        this.applicationContext = applicationContext;
    }

    @Override
    public ResultadoConstrucao executar(String ticker, LocalDate dataBase, boolean forcarRecalculo) {
        long inicio = System.currentTimeMillis();
        log.info("Iniciando construção de curva. Ticker: {}, DataBase: {}, ForçarRecálculo: {}", ticker, dataBase, forcarRecalculo);

        // 1. Resolução de configuração
        ContextoConstrucaoCurva contexto = persistencePort.obterContextoConfiguracao(ticker, dataBase)
            .orElseThrow(() -> new NotFoundException(
                "CURV-CONFIG-NOT-FOUND",
                "Configuração não encontrada para o ticker: " + ticker
            ));

        // 2. Resolução do Builder: Coexistência de Modelo Nativo Java e Dinâmico Groovy
        String motorNome = persistencePort.obterMotorConfigurado(ticker);
        if (motorNome == null || motorNome.isBlank()) {
            motorNome = contexto.getParametro("cMotorCalc", "FlatForwardCurveInterpolator");
        }

        CurveBuilderStrategy strategy = resolveStrategy(motorNome);

        // 3. Resolução de insumos
        List<VerticeInsumo> insumos = persistencePort.carregarInsumos(ticker, dataBase);
        if (insumos == null || insumos.isEmpty()) {
            throw new NotFoundException(
                "CURV-INPUT-DATA-NOT-FOUND",
                "Insumos brutos ausentes em dbo.tDadoCurva para ticker " + ticker + " na data " + dataBase
            );
        }

        if (insumos.size() < 2) {
            throw new BusinessException(
                "CURV-INSUFFICIENT-INPUTS",
                "Quantidade insuficiente de insumos para o cálculo (mínimo 2)."
            );
        }

        // 4. Execução do algoritmo de construção
        List<VerticeCalculado> verticesCalculados = strategy.build(contexto, insumos);

        // 5. Persistência consolidada idempotente
        persistencePort.salvarVerticesConsolidados(ticker, dataBase, verticesCalculados);

        long tempoMs = System.currentTimeMillis() - inicio;
        log.info("Construção de curva concluída. Ticker: {}, Estratégia: {}, Vértices: {}, Tempo: {}ms",
            ticker, strategy.getStrategyName(), verticesCalculados.size(), tempoMs);

        return new ResultadoConstrucao(
            ticker,
            dataBase,
            strategy.getStrategyName(),
            verticesCalculados.size(),
            tempoMs,
            verticesCalculados
        );
    }

    private CurveBuilderStrategy resolveStrategy(String motorNome) {
        // A. Primeiro tenta no Registry (onde residem tanto os Java canônicos quanto os Groovy registrados)
        if (registry.contains(motorNome)) {
            return registry.find(motorNome).orElseThrow();
        }

        // B. Tenta no ApplicationContext do Spring (caso tenha sido injetado dinamicamente via Groovy upload)
        if (applicationContext.containsBean(motorNome)) {
            Object bean = applicationContext.getBean(motorNome);
            if (bean instanceof CurveBuilderStrategy s) {
                registry.register(s);
                return s;
            } else if (bean instanceof CurveInterpolator interpolator) {
                registry.register(interpolator);
                registry.find(interpolator.getClass().getSimpleName()).ifPresent(builder -> registry.register(motorNome, builder));
                return registry.find(motorNome).orElseThrow();
            }
        }

        throw new NotFoundException(
            "CURV-BUILDER-NOT-FOUND",
            "Estratégia de cálculo não encontrada nem no Registry nativo nem no contexto Spring: " + motorNome
        );
    }
}
