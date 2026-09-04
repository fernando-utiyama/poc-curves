package com.poccurves.orchestrator.adapter.out.persistence;

import com.poccurves.orchestrator.domain.EstadoPendenciaDlq;
import com.poccurves.orchestrator.domain.PendenciaDlq;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Teste de integração real: exige o SQL Server local de pé, migrado até V13.
 * Verifica {@link PendenciaDlqRepository} de verdade contra o banco — auditoria
 * manual desta sessão do repositório escrito pelo agy.
 */
class PendenciaDlqRepositoryIT {

    private static JdbcTemplate jdbcTemplateSa() {
        DriverManagerDataSource ds = new DriverManagerDataSource(
                "jdbc:sqlserver://localhost:1433;databaseName=curvasdb;trustServerCertificate=true;encrypt=true",
                "sa", "CurvasP0c!Local");
        ds.setDriverClassName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
        return new JdbcTemplate(ds);
    }

    private final PendenciaDlqRepository repository = new PendenciaDlqRepository(jdbcTemplateSa());

    @AfterEach
    void limpar() {
        jdbcTemplateSa().update("DELETE FROM pendencia_dlq WHERE id_evento LIKE 'it-auditoria-%'");
    }

    @Test
    void insereBuscaPorIdEventoEAtualizaComRoundTripCompleto() {
        String idEvento = "it-auditoria-" + UUID.randomUUID();
        UUID correlacaoId = UUID.randomUUID();
        Instant falhouEm = Instant.parse("2026-08-22T14:30:00Z");

        PendenciaDlq pendencia = PendenciaDlq.abrir(
                idEvento, correlacaoId, "PARSER_INVALIDO", "detalhe do erro",
                "B3", "PRECOS", java.time.LocalDate.of(2026, 8, 22),
                "marketdata-raw.b3", 2, 12345L,
                "marketdata-raw.b3.dlq", 2, 7L,
                "curve-processor-grupo", "1.0.0", falhouEm
        );

        repository.inserir(pendencia);
        assertThat(pendencia.id()).isNotNull();

        Optional<PendenciaDlq> lidaOpt = repository.buscarPorIdEvento(idEvento);
        assertThat(lidaOpt).isPresent();
        PendenciaDlq lida = lidaOpt.get();
        assertThat(lida.id()).isEqualTo(pendencia.id());
        assertThat(lida.correlacaoId()).isEqualTo(correlacaoId);
        assertThat(lida.motivo()).isEqualTo("PARSER_INVALIDO");
        assertThat(lida.topicoOrigem()).isEqualTo("marketdata-raw.b3");
        assertThat(lida.particaoOrigem()).isEqualTo(2);
        assertThat(lida.offsetOrigem()).isEqualTo(12345L);
        assertThat(lida.tentativas()).isEqualTo(1);
        assertThat(lida.estado()).isEqualTo(EstadoPendenciaDlq.ABERTA);
        assertThat(lida.falhouEm()).isEqualTo(falhouEm);

        // Exercita a máquina de estados reidratada e persiste a transição
        lida.iniciarReprocessamento();
        lida.voltarParaAberta();
        repository.atualizar(lida);

        PendenciaDlq relida = repository.buscarPorId(pendencia.id()).orElseThrow();
        assertThat(relida.estado()).isEqualTo(EstadoPendenciaDlq.ABERTA);
        assertThat(relida.tentativas()).isEqualTo(2);
    }

    @Test
    void descarteComResponsavelEJustificativaPersisteDesfecho() {
        String idEvento = "it-auditoria-" + UUID.randomUUID();
        PendenciaDlq pendencia = PendenciaDlq.abrir(
                idEvento, null, "SCHEMA_INVALIDO", null,
                null, null, null,
                "curve.published.v1", null, null,
                "curve.published.v1.dlq", null, null,
                null, null, Instant.parse("2026-08-22T10:00:00Z")
        );
        repository.inserir(pendencia);

        pendencia.descartar("operador-teste", "Dado obsoleto, não vale mais reprocessar");
        repository.atualizar(pendencia);

        PendenciaDlq relida = repository.buscarPorId(pendencia.id()).orElseThrow();
        assertThat(relida.estado()).isEqualTo(EstadoPendenciaDlq.DESCARTADA);
        assertThat(relida.responsavel()).isEqualTo("operador-teste");
        assertThat(relida.justificativa()).isEqualTo("Dado obsoleto, não vale mais reprocessar");
        assertThat(relida.desfechoEm()).isNotNull();
        assertThat(relida.correlacaoId()).isNull();
    }
}
