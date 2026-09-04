package com.poccurves.orchestrator.adapter.out.persistence;

import com.poccurves.orchestrator.domain.EstadoExecucao;
import com.poccurves.orchestrator.domain.ExecucaoCurva;
import com.poccurves.orchestrator.domain.Faixa;
import com.poccurves.orchestrator.domain.MomentoCurva;
import com.poccurves.orchestrator.domain.TipoDisparo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Teste de integração real: exige o SQL Server local de pé, migrado até V13.
 * Verifica {@link ExecucaoCurvaRepository} de verdade contra o banco — não é
 * teste de unidade com mock, é a auditoria manual desta sessão (agy escreveu
 * o repositório, este teste comprova que ele funciona contra o schema real).
 */
class ExecucaoCurvaRepositoryIT {

    private static JdbcTemplate jdbcTemplateSa() {
        DriverManagerDataSource ds = new DriverManagerDataSource(
                "jdbc:sqlserver://localhost:1433;databaseName=curvasdb;trustServerCertificate=true;encrypt=true",
                "sa", "CurvasP0c!Local");
        ds.setDriverClassName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
        return new JdbcTemplate(ds);
    }

    private final ExecucaoCurvaRepository repository = new ExecucaoCurvaRepository(jdbcTemplateSa());

    @AfterEach
    void limpar() {
        JdbcTemplate sa = jdbcTemplateSa();
        sa.update("DELETE FROM execucao_curva WHERE conjunto_dados LIKE 'IT_AUDITORIA_%'");
        sa.update("DELETE FROM definicao_curva WHERE codigo LIKE 'IT_AUDITORIA_%'");
    }

    /** execucao_curva.definicao_curva_id tem FK real para definicao_curva — precisa existir antes. */
    private UUID semearDefinicao(JdbcTemplate sa, String codigo) {
        UUID definicaoId = UUID.randomUUID();
        sa.update(
                "INSERT INTO definicao_curva (id, codigo, nome, moeda, modo_origem, horario_limite_publicacao, estado, criado_por) " +
                        "VALUES (?, ?, ?, 'BRL', 'BOOTSTRAPPED', '18:00', 'ATIVA', 'teste-auditoria')",
                definicaoId.toString(), codigo, codigo);
        return definicaoId;
    }

    @Test
    void insereBuscaAtualizaERoundTripComTodosOsCampos() {
        UUID correlacaoId = UUID.randomUUID();
        String conjuntoDados = "IT_AUDITORIA_" + UUID.randomUUID();
        LocalDate data = LocalDate.of(2026, 8, 22);

        ExecucaoCurva execucao = ExecucaoCurva.iniciar(
                correlacaoId, null, null, conjuntoDados, data, MomentoCurva.FECHAMENTO,
                TipoDisparo.MANUAL, "teste-auditoria", Faixa.PRIORITARIA,
                java.time.Instant.parse("2026-08-22T18:00:00Z"), 3600
        );

        repository.inserir(execucao);

        Optional<ExecucaoCurva> lidoOpt = repository.buscarPorId(execucao.id());
        assertThat(lidoOpt).isPresent();
        ExecucaoCurva lido = lidoOpt.get();
        assertThat(lido.id()).isEqualTo(execucao.id());
        assertThat(lido.correlacaoId()).isEqualTo(correlacaoId);
        assertThat(lido.conjuntoDados()).isEqualTo(conjuntoDados);
        assertThat(lido.dataReferencia()).isEqualTo(data);
        assertThat(lido.momentoCurva()).isEqualTo(MomentoCurva.FECHAMENTO);
        assertThat(lido.disparo()).isEqualTo(TipoDisparo.MANUAL);
        assertThat(lido.faixa()).isEqualTo(Faixa.PRIORITARIA);
        assertThat(lido.estado()).isEqualTo(EstadoExecucao.PENDENTE);
        assertThat(lido.margemSegundos()).isEqualTo(3600);

        // Antes de atualizar: verifica a checagem de duplicidade ativa
        boolean ativaAntes = repository.existeExecucaoAtivaParaConjuntoDados(conjuntoDados, data, MomentoCurva.FECHAMENTO);
        assertThat(ativaAntes).isTrue();

        execucao.iniciarExecucao();
        execucao.falhar("ERRO_TESTE", "Falha simulada pela auditoria");
        repository.atualizar(execucao);

        ExecucaoCurva relido = repository.buscarPorId(execucao.id()).orElseThrow();
        assertThat(relido.estado()).isEqualTo(EstadoExecucao.FALHOU);
        assertThat(relido.codigoErro()).isEqualTo("ERRO_TESTE");
        assertThat(relido.mensagemErro()).isEqualTo("Falha simulada pela auditoria");
        assertThat(relido.finalizadoEm()).isNotNull();

        // Depois de FALHOU (estado terminal): não deve mais contar como ativa
        boolean ativaDepois = repository.existeExecucaoAtivaParaConjuntoDados(conjuntoDados, data, MomentoCurva.FECHAMENTO);
        assertThat(ativaDepois).isFalse();
    }

    @Test
    void existeExecucaoAtivaParaDefinicaoCurvaDetectaDuplicidadePorCurva() {
        String codigo = "IT_AUDITORIA_" + UUID.randomUUID();
        UUID definicaoCurvaId = semearDefinicao(jdbcTemplateSa(), codigo);
        LocalDate data = LocalDate.of(2026, 8, 22);
        String conjuntoDados = "IT_AUDITORIA_" + UUID.randomUUID();

        ExecucaoCurva execucao = ExecucaoCurva.iniciar(
                UUID.randomUUID(), null, definicaoCurvaId, conjuntoDados, data, MomentoCurva.ABERTURA,
                TipoDisparo.CARGA_MANUAL, "teste-auditoria", Faixa.PRIORITARIA, null, null
        );
        repository.inserir(execucao);

        assertThat(repository.existeExecucaoAtivaParaDefinicaoCurva(definicaoCurvaId, data, MomentoCurva.ABERTURA)).isTrue();
        assertThat(repository.existeExecucaoAtivaParaDefinicaoCurva(UUID.randomUUID(), data, MomentoCurva.ABERTURA)).isFalse();
    }
}
