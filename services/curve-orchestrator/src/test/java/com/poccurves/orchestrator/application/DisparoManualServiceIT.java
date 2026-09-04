package com.poccurves.orchestrator.application;

import tools.jackson.databind.ObjectMapper;
import com.poccurves.orchestrator.adapter.out.http.CurveEngineClient;
import com.poccurves.orchestrator.adapter.out.http.FunctionMarketdataClient;
import com.poccurves.orchestrator.adapter.out.persistence.DefinicaoCurvaConsultaRepository;
import com.poccurves.orchestrator.adapter.out.persistence.ExecucaoCurvaRepository;
import com.poccurves.orchestrator.domain.EstadoExecucao;
import com.poccurves.orchestrator.domain.ExecucaoCurva;
import com.poccurves.orchestrator.domain.Faixa;
import com.poccurves.orchestrator.domain.MomentoCurva;
import com.poccurves.orchestrator.domain.TipoDisparo;
import com.poccurves.orchestrator.dto.OrchestratorDtos.DisparoManualRequest;
import com.poccurves.orchestrator.dto.OrchestratorDtos.DisparoManualResponse;
import com.poccurves.orchestrator.dto.OrchestratorDtos.ProgressoConjuntoDTO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Teste de integração real: exige o SQL Server local de pé (migrado até V13),
 * o container `function-marketdata-http` de pé na porta 8091, e o `curve-engine`
 * de pé na porta 8083 (deploy/podman/compose.yaml) — despacho de construção via
 * REST (D1d), não mais Kafka.
 * Verifica {@link DisparoManualService} de ponta a ponta — auditoria manual desta sessão.
 */
class DisparoManualServiceIT {

    private static JdbcTemplate jdbcTemplateSa() {
        DriverManagerDataSource ds = new DriverManagerDataSource(
                "jdbc:sqlserver://localhost:1433;databaseName=curvasdb;trustServerCertificate=true;encrypt=true",
                "sa", "CurvasP0c!Local");
        ds.setDriverClassName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
        return new JdbcTemplate(ds);
    }

    private static RestClient feederClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(60));
        factory.setReadTimeout(Duration.ofSeconds(60));
        return RestClient.builder().baseUrl("http://localhost:8091").requestFactory(factory).build();
    }

    private static RestClient curveEngineRestClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(5));
        return RestClient.builder().baseUrl("http://localhost:8083").requestFactory(factory).build();
    }

    private final ExecucaoCurvaRepository repository = new ExecucaoCurvaRepository(jdbcTemplateSa());
    private final DefinicaoCurvaConsultaRepository definicaoCurvaConsultaRepository =
            new DefinicaoCurvaConsultaRepository(jdbcTemplateSa(), new ObjectMapper());
    private final CurveEngineClient curveEngineClient = new CurveEngineClient(curveEngineRestClient());
    private final AquisicaoExecutionService aquisicaoExecutionService = new AquisicaoExecutionService(
            new FunctionMarketdataClient(feederClient()), definicaoCurvaConsultaRepository, curveEngineClient, 3);
    private final DisparoManualService service = new DisparoManualService(repository, aquisicaoExecutionService);

    @AfterEach
    void limpar() {
        jdbcTemplateSa().update("DELETE FROM execucao_curva WHERE conjunto_dados LIKE 'IT[DSU]_%' OR conjunto_dados = 'BVBG.086'");
    }

    @Test
    void disparoManualParaDataFuturaSemDadoPersisteExecucaoSemDado() {
        // Precisa ser um dataset real reconhecido pelo RegistroFeeders (function-marketdata) —
        // um nome inventado faz o feeder devolver 400 (DatasetNaoSuportadoError), não NO_DATA.
        String conjunto = "BVBG.086";
        DisparoManualRequest request = new DisparoManualRequest(
                LocalDate.of(2035, 6, 15), List.of(conjunto), "teste de auditoria");

        DisparoManualResponse resposta = service.disparoManual(request, "auditor-teste");

        assertThat(resposta.status()).isEqualTo("DISPARADO");
        assertThat(resposta.correlationId()).isNotBlank();
        assertThat(resposta.progressoPorConjunto()).hasSize(1);

        ProgressoConjuntoDTO item = resposta.progressoPorConjunto().get(0);
        assertThat(item.conjunto()).isEqualTo(conjunto);
        assertThat(item.status()).isEqualTo("SEM_DADO");

        // Confirma que o estado foi realmente persistido, não só devolvido na resposta
        Optional<ExecucaoCurva> persistida = repository.buscarExecucaoAtivaParaConjuntoDados(
                conjunto, LocalDate.of(2035, 6, 15), MomentoCurva.INTRADIA);
        // SEM_DADO é terminal, então não deve aparecer como "ativa"
        assertThat(persistida).isEmpty();

        List<java.util.Map<String, Object>> linhas = jdbcTemplateSa().queryForList(
                "SELECT estado, correlacao_id FROM execucao_curva WHERE conjunto_dados = ?", conjunto);
        assertThat(linhas).hasSize(1);
        assertThat(linhas.get(0).get("estado")).isEqualTo(EstadoExecucao.SEM_DADO.name());
        assertThat(linhas.get(0).get("correlacao_id").toString()).isEqualToIgnoringCase(resposta.correlationId());
    }

    @Test
    void disparoManualParaDataQueNaoEhPregaoRecusaSemCriarExecucao() {
        String conjunto = "ITS_" + UUID.randomUUID().toString().substring(0, 8);
        LocalDate sabado = LocalDate.of(2026, 8, 22);
        DisparoManualRequest request = new DisparoManualRequest(sabado, List.of(conjunto), "teste");

        DisparoManualResponse resposta = service.disparoManual(request, "auditor-teste");

        assertThat(resposta.status()).isEqualTo("RECUSADO_NAO_PREGAO");
        assertThat(resposta.progressoPorConjunto()).isEmpty();

        Integer count = jdbcTemplateSa().queryForObject(
                "SELECT COUNT(*) FROM execucao_curva WHERE conjunto_dados = ?", Integer.class, conjunto);
        assertThat(count).isZero();
    }

    @Test
    void disparoManualParaConjuntoJaAtivoNaoCriaSegundaExecucaoNemChamaOFeeder() {
        String conjunto = "ITU_" + UUID.randomUUID().toString().substring(0, 8);
        LocalDate data = LocalDate.of(2035, 6, 15);
        UUID correlacaoOriginal = UUID.randomUUID();

        ExecucaoCurva existente = ExecucaoCurva.iniciar(
                correlacaoOriginal, null, null, conjunto, data,
                MomentoCurva.INTRADIA,
                TipoDisparo.MANUAL, "outro-operador",
                Faixa.PRIORITARIA, null, null);
        repository.inserir(existente);
        existente.iniciarExecucao();
        repository.atualizar(existente);

        DisparoManualRequest request = new DisparoManualRequest(data, List.of(conjunto), "teste");
        DisparoManualResponse resposta = service.disparoManual(request, "auditor-teste");

        assertThat(resposta.status()).isEqualTo("JA_EM_ANDAMENTO");
        assertThat(resposta.progressoPorConjunto()).hasSize(1);
        assertThat(resposta.progressoPorConjunto().get(0).mensagem()).contains(correlacaoOriginal.toString());

        // Confirma que NÃO foi criada uma segunda linha para o mesmo conjunto/data
        Integer count = jdbcTemplateSa().queryForObject(
                "SELECT COUNT(*) FROM execucao_curva WHERE conjunto_dados = ?", Integer.class, conjunto);
        assertThat(count).isEqualTo(1);

        // Confirma que o estado da execução existente não foi alterado (o feeder não foi chamado para ela)
        String estadoAtual = jdbcTemplateSa().queryForObject(
                "SELECT estado FROM execucao_curva WHERE conjunto_dados = ?", String.class, conjunto);
        assertThat(estadoAtual).isEqualTo(EstadoExecucao.EXECUTANDO.name());
    }

    @Test
    void disparoManualComListaDeConjuntosVaziaLancaIllegalArgumentException() {
        DisparoManualRequest request = new DisparoManualRequest(LocalDate.of(2035, 6, 15), List.of(), "teste");
        assertThatThrownBy(() -> service.disparoManual(request, "auditor-teste"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
