package com.poccurves.orchestrator.adapter.out.persistence;

import com.poccurves.orchestrator.domain.Agendamento;
import com.poccurves.orchestrator.domain.ExecucaoCurva;
import com.poccurves.orchestrator.domain.Faixa;
import com.poccurves.orchestrator.domain.MomentoCurva;
import com.poccurves.orchestrator.domain.TipoDisparo;
import com.poccurves.orchestrator.domain.AgendamentoComUltimaExecucao;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AgendamentoRepositoryIT {

    private static JdbcTemplate jdbcTemplate() {
        DriverManagerDataSource ds = new DriverManagerDataSource(
                "jdbc:sqlserver://localhost:1433;databaseName=curvasdb;trustServerCertificate=true;encrypt=true",
                "curve_orchestrator_app", "CurveOrchestratorP0c!Local");
        ds.setDriverClassName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
        return new JdbcTemplate(ds);
    }

    private static JdbcTemplate jdbcTemplateSa() {
        DriverManagerDataSource ds = new DriverManagerDataSource(
                "jdbc:sqlserver://localhost:1433;databaseName=curvasdb;trustServerCertificate=true;encrypt=true",
                "sa", "CurvasP0c!Local");
        ds.setDriverClassName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
        return new JdbcTemplate(ds);
    }

    private final JdbcTemplate jdbcTemplate = jdbcTemplate();
    private final AgendamentoRepository repository = new AgendamentoRepository(jdbcTemplate);
    private final ExecucaoCurvaRepository execucaoRepository = new ExecucaoCurvaRepository(jdbcTemplate);

    @AfterEach
    void limpar() {
        // curve_orchestrator_app não tem GRANT DELETE (fronteira de escrita restrita, V13) —
        // limpeza de teste precisa da credencial sa, mesmo padrão dos demais IT do módulo.
        JdbcTemplate sa = jdbcTemplateSa();
        sa.update("DELETE FROM execucao_curva WHERE conjunto_dados LIKE 'IT_%'");
        sa.update("DELETE FROM agendamento_curva WHERE conjunto_dados LIKE 'IT_%'");
    }

    @Test
    void fluxoCompletoDePersistenciaEBuscaComUltimaExecucao() {
        // Inserir
        String conjunto = "IT_CONJUNTO_" + UUID.randomUUID().toString().substring(0, 8);
        Agendamento a = Agendamento.criar(
                null, conjunto, MomentoCurva.INTRADIA, Faixa.ROTINA,
                "0 0 12 * * ?", "America/Sao_Paulo", 30, 60, "teste"
        );
        repository.inserir(a);

        // Buscar
        Optional<Agendamento> buscado = repository.buscarPorId(a.id());
        assertThat(buscado).isPresent();
        assertThat(buscado.get().conjuntoDados()).isEqualTo(conjunto);
        assertThat(buscado.get().ativo()).isTrue();

        // Editar
        buscado.get().editar("0 0 13 * * ?", "UTC", 45, 120, Faixa.PRIORITARIA);
        repository.atualizar(buscado.get());

        Optional<Agendamento> editado = repository.buscarPorId(a.id());
        assertThat(editado.get().fusoHorario()).isEqualTo("UTC");
        assertThat(editado.get().janelaTentativaMinutos()).isEqualTo(45);
        assertThat(editado.get().faixa()).isEqualTo(Faixa.PRIORITARIA);

        // Ativar/Desativar
        editado.get().desativar();
        repository.atualizar(editado.get());
        assertThat(repository.buscarPorId(a.id()).get().ativo()).isFalse();
        assertThat(repository.listarAtivos()).noneMatch(ag -> ag.id().equals(a.id()));

        // BuscarComUltimaExecucao
        ExecucaoCurva exec = ExecucaoCurva.iniciar(
                UUID.randomUUID(), null, null, conjunto, LocalDate.now(),
                MomentoCurva.INTRADIA, TipoDisparo.AGENDADO, "sistema", Faixa.PRIORITARIA, null, null
        );
        execucaoRepository.inserir(exec);
        repository.vincularExecucao(exec.id(), a.id());

        exec.iniciarExecucao();
        exec.falhar("ERRO_IT", "Falha de teste");
        execucaoRepository.atualizar(exec);

        List<AgendamentoComUltimaExecucao> listagem = repository.buscarComUltimaExecucao();
        Optional<AgendamentoComUltimaExecucao> comUltima = listagem.stream()
                .filter(item -> item.agendamento().id().equals(a.id()))
                .findFirst();
        
        assertThat(comUltima).isPresent();
        assertThat(comUltima.get().ultimaExecucaoEstado()).isEqualTo("FALHOU");
        assertThat(comUltima.get().ultimaExecucaoFinalizadoEm()).isNotNull();
    }
}
