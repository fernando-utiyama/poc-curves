package com.poccurves.processor.application;

import com.poccurves.processor.CurveProcessorApplication;
import com.poccurves.processor.domain.PontoDadoMercado;
import com.poccurves.processor.domain.ResultadoProcessamentoBloco;
import com.poccurves.processor.domain.TipoPayload;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Teste de integracao real: exige o ambiente local Podman de pe
 * (`deploy/podman/up.sh --lite`), com o SQL Server acessivel em
 * localhost:1433 e migrado ate V11.
 * <p>
 * Cobre a tarefa 8.19 do backlog: o mesmo lote (mesmo loteExternoId, mesmo
 * eventId) chegando por duas faixas de consumo concorrentes (ex. rotina e
 * prioritaria, cada uma seu proprio consumer group/thread) nao pode resultar
 * em duas linhas de lote_ingestao nem em pontos duplicados. Modela a
 * concorrencia entre faixas com duas threads Java chamando
 * {@link IngestaoService#processarBloco} simultaneamente (sincronizadas por
 * {@link CyclicBarrier} para maximizar a chance real de corrida no banco) em
 * vez de depender do timing de poll do Kafka real para produzir a corrida —
 * a fonte da concorrencia sao as faixas distintas, mas a propriedade a provar
 * e sobre o codigo de persistencia, que e exatamente o que duas threads
 * concorrentes chamando o mesmo metodo de servico exercitam de forma
 * deterministica.
 * <p>
 * Antes desta sessao a tabela nao tinha restricao UNIQUE em
 * lote_externo_id — a corrida resultava em duas linhas duplicadas (ou, em
 * cenarios de timing diferente, uma excecao de violacao de restricao
 * propagando sem tratamento). A migracao V11 adiciona a restricao e
 * {@code IngestaoService.processarBloco} agora recupera dela, recarregando o
 * lote vencedor da corrida em vez de duplicar ou falhar.
 */
@SpringBootTest(classes = CurveProcessorApplication.class)
class ProcessarBlocoConcorrenteIT {

    @DynamicPropertySource
    static void datasourceLocal(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",
                () -> "jdbc:sqlserver://localhost:1433;databaseName=curvasdb;trustServerCertificate=true;encrypt=true");
        registry.add("spring.kafka.bootstrap-servers", () -> "localhost:19092");
    }

    @Autowired
    private IngestaoService ingestaoService;

    private static JdbcTemplate jdbcTemplateSa() {
        DriverManagerDataSource ds = new DriverManagerDataSource(
                "jdbc:sqlserver://localhost:1433;databaseName=curvasdb;trustServerCertificate=true;encrypt=true",
                "sa", "CurvasP0c!Local");
        ds.setDriverClassName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
        return new JdbcTemplate(ds);
    }

    private void limparDadosDeTeste() {
        JdbcTemplate sa = jdbcTemplateSa();
        sa.update("DELETE FROM ponto_dado_mercado WHERE chave_instrumento LIKE 'CONCORRENCIA_%'");
        sa.update("DELETE FROM lote_ingestao WHERE lote_externo_id LIKE 'it-concorrencia-%'");
    }

    @AfterEach
    void depois() {
        limparDadosDeTeste();
    }

    @Test
    void mesmoLoteChegandoPorDuasFaixasConcorrentesNaoDuplica() throws Exception {
        String loteExternoId = "it-concorrencia-" + UUID.randomUUID();
        String eventId = "evt-concorrencia-1";
        LocalDate dataRef = LocalDate.of(2026, 8, 21);

        PontoDadoMercado ponto = new PontoDadoMercado(
                "B3", "BVBG.086", dataRef, "CONCORRENCIA_PONTO",
                new BigDecimal("13.500"), "TAXA_AJUSTE", null);

        CyclicBarrier largada = new CyclicBarrier(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Callable<ResultadoProcessamentoBloco> chamada = () -> {
                largada.await(10, TimeUnit.SECONDS);
                return ingestaoService.processarBloco(
                        "B3", "BVBG.086", dataRef, loteExternoId,
                        UUID.randomUUID(), eventId, "sha256:" + "d".repeat(64), 1, 1,
                        TipoPayload.INDIVIDUAL_QUOTES, List.of(ponto));
            };

            Future<ResultadoProcessamentoBloco> faixaRotina = pool.submit(chamada);
            Future<ResultadoProcessamentoBloco> faixaPrioritaria = pool.submit(chamada);

            // Nenhuma das duas chamadas concorrentes pode propagar excecao —
            // a restricao UNIQUE + o catch em IngestaoService devem absorver a corrida.
            ResultadoProcessamentoBloco r1 = faixaRotina.get(15, TimeUnit.SECONDS);
            ResultadoProcessamentoBloco r2 = faixaPrioritaria.get(15, TimeUnit.SECONDS);

            assertThat(r1.lote().loteExternoId()).isEqualTo(loteExternoId);
            assertThat(r2.lote().loteExternoId()).isEqualTo(loteExternoId);
        } finally {
            pool.shutdown();
        }

        JdbcTemplate sa = jdbcTemplateSa();
        Integer contagemLotes = sa.queryForObject(
                "SELECT COUNT(*) FROM lote_ingestao WHERE lote_externo_id = ?",
                Integer.class, loteExternoId);
        Integer pontosGravadosNoLote = sa.queryForObject(
                "SELECT pontos_gravados FROM lote_ingestao WHERE lote_externo_id = ?",
                Integer.class, loteExternoId);
        String estadoFinal = sa.queryForObject(
                "SELECT estado FROM lote_ingestao WHERE lote_externo_id = ?",
                String.class, loteExternoId);
        Integer contagemPontos = sa.queryForObject(
                "SELECT COUNT(*) FROM ponto_dado_mercado WHERE chave_instrumento = 'CONCORRENCIA_PONTO'",
                Integer.class);

        assertThat(contagemLotes)
                .as("as duas faixas concorrentes nao podem ter criado duas linhas de lote_ingestao")
                .isEqualTo(1);
        assertThat(estadoFinal).isEqualTo("COMPLETO");
        assertThat(pontosGravadosNoLote).isEqualTo(1);
        assertThat(contagemPontos)
                .as("o ponto de mercado nao pode ter sido gravado duas vezes")
                .isEqualTo(1);
    }
}
