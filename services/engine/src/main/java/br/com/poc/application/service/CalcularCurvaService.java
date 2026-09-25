package br.com.poc.application.service;

import br.com.poc.application.exception.BusinessException;
import br.com.poc.application.exception.NotFoundException;
import br.com.poc.application.port.in.CalcularCurvaUseCase;
import br.com.poc.application.port.out.CurvaPersistencePort;
import br.com.poc.domain.model.*;
import br.com.poc.domain.service.CurvaInterpolacaoDomainService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
public class CalcularCurvaService implements CalcularCurvaUseCase {

    private static final Logger log = LoggerFactory.getLogger(CalcularCurvaService.class);

    private final CurvaPersistencePort persistencePort;
    private final CurvaInterpolacaoDomainService interpolacaoDomainService = new CurvaInterpolacaoDomainService();

    public CalcularCurvaService(CurvaPersistencePort persistencePort) { this.persistencePort = persistencePort; }

    @Override
    public ResultadoCalculo executar(
        String ticker,
        LocalDate dataBase,
        Integer base,
        MetodoInterpolacao metodo,
        PoliticaExtrapolacao politica,
        List<Integer> prazos
    ) {
        int baseFinal = (base != null && base > 0) ? base : 252;
        MetodoInterpolacao metodoFinal = (metodo != null) ? metodo : MetodoInterpolacao.EXPONENCIAL;

        // 1. Resolução em cascata da política de extrapolação
        PoliticaExtrapolacao politicaFinal = politica;
        if (politicaFinal == null) {
            Optional<ContextoConstrucaoCurva> ctx = persistencePort.obterContextoConfiguracao(ticker, dataBase);
            String polConfig = ctx.map(c -> c.getParametro("POLITICA_EXTRAPOLACAO", null)).orElse(null);
            if (polConfig != null) {
                try {
                    politicaFinal = PoliticaExtrapolacao.valueOf(polConfig.toUpperCase());
                } catch (IllegalArgumentException e) {
                    politicaFinal = PoliticaExtrapolacao.LINEAR;
                }
            } else {
                politicaFinal = PoliticaExtrapolacao.LINEAR; // Retrocompatibilidade padrão
            }
        }

        // 2. Carregamento dos vértices consolidados de dbo.tDadoVertcCurva
        List<VerticeCalculado> vertices = persistencePort.carregarVerticesConsolidados(ticker, dataBase);
        if (vertices == null || vertices.isEmpty()) {
            throw new NotFoundException(
                "CURV-NO-VERTICES",
                "Não há vértices consolidados em dbo.tDadoVertcCurva para a curva '" + ticker + "' na data base " + dataBase
            );
        }

        // 3. Execução analítica de interpolação e extrapolação
        try {
            List<PontoInterpolado> pontos = interpolacaoDomainService.interpolar(
                vertices,
                prazos,
                baseFinal,
                metodoFinal,
                politicaFinal
            );

            return new ResultadoCalculo(
                ticker,
                dataBase,
                baseFinal,
                metodoFinal,
                politicaFinal,
                pontos
            );
        } catch (IllegalStateException e) {
            if (e.getMessage() != null && e.getMessage().contains("CURV-OUT-OF-DOMAIN")) {
                throw new BusinessException("CURV-OUT-OF-DOMAIN", e.getMessage());
            }
            throw new BusinessException("CURV-INTERPOLATION-ERROR", e.getMessage());
        }
    }
}
