package com.poccurves.orchestrator.adapter.out.persistence;

import com.poccurves.orchestrator.domain.ExecucaoCurva;
import com.poccurves.orchestrator.domain.Faixa;
import com.poccurves.orchestrator.domain.MomentoCurva;
import com.poccurves.orchestrator.domain.TipoDisparo;
import com.poccurves.orchestrator.domain.PaginaExecucoes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Teste de integração real: exige o SQL Server local de pé, migrado até V13.
 * Verifica {@link ExecucaoCurvaRepository#buscarComFiltros} de verdade — JOIN com
 * definicao_curva, filtros opcionais e paginação/ordenação, contra o schema real.
 */
class ExecucaoCurvaRepositoryConsultaIT {

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
        sa.update("DELETE FROM execucao_curva WHERE conjunto_dados LIKE 'ITQ_%'");
        sa.update("DELETE FROM definicao_curva WHERE codigo LIKE 'ITQ_%'");
    }

    private UUID semearDefinicao(JdbcTemplate sa, String codigo) {
        UUID id = UUID.randomUUID();
        sa.update(
                "INSERT INTO definicao_curva (id, codigo, nome, moeda, modo_origem, horario_limite_publicacao, estado, criado_por) " +
                        "VALUES (?, ?, ?, 'BRL', 'BOOTSTRAPPED', '18:00', 'ATIVA', 'teste-consulta')",
                id.toString(), codigo, codigo);
        return id;
    }

    @Test
    void buscarComFiltrosResolveCodigoCurvaViaJoinEFiltraPorEstadoEData() {
        String codigo = "ITQ_" + UUID.randomUUID().toString().substring(0, 8);
        UUID definicaoId = semearDefinicao(jdbcTemplateSa(), codigo);
        LocalDate data = LocalDate.of(2030, 3, 10);

        ExecucaoCurva comCurva = ExecucaoCurva.iniciar(
                UUID.randomUUID(), null, definicaoId, "ITQ_conjunto", data, MomentoCurva.FECHAMENTO,
                TipoDisparo.CARGA_MANUAL, "op1", Faixa.PRIORITARIA, null, null);
        repository.inserir(comCurva);
        comCurva.iniciarExecucao();
        comCurva.iniciarConstrucao();
        comCurva.concluir();
        repository.atualizar(comCurva);

        // Execução sem definicao_curva_id (fluxo por conjunto de dados) — não deve aparecer no filtro por "codigo"
        ExecucaoCurva semCurva = ExecucaoCurva.iniciar(
                UUID.randomUUID(), null, null, "ITQ_outro_conjunto", data, MomentoCurva.FECHAMENTO,
                TipoDisparo.MANUAL, "op2", Faixa.PRIORITARIA, null, null);
        repository.inserir(semCurva);

        PaginaExecucoes pagina = repository.buscarComFiltros(codigo, null, null, 0, 10);

        assertThat(pagina.totalElementos()).isEqualTo(1);
        assertThat(pagina.itens()).hasSize(1);
        var item = pagina.itens().get(0);
        assertThat(item.id()).isEqualTo(comCurva.id());
        assertThat(item.codigoCurva()).isEqualTo(codigo);
        assertThat(item.estado()).isEqualTo("CONCLUIDA");
        assertThat(item.tipoDisparo()).isEqualTo("CARGA_MANUAL");

        // Filtro por conjunto_dados direto (execução sem definicao_curva_id) também deve funcionar
        PaginaExecucoes paginaPorConjunto = repository.buscarComFiltros("ITQ_outro_conjunto", null, null, 0, 10);
        assertThat(paginaPorConjunto.totalElementos()).isEqualTo(1);
        assertThat(paginaPorConjunto.itens().get(0).codigoCurva()).isEqualTo("ITQ_outro_conjunto");

        // Filtro por estado que não bate com nenhuma das duas
        PaginaExecucoes paginaEstadoErrado = repository.buscarComFiltros(codigo, null, "FALHOU", 0, 10);
        assertThat(paginaEstadoErrado.totalElementos()).isZero();
    }

    @Test
    void buscarComFiltrosOrdenaPorInicioMaisRecenteEPaginaCorretamente() throws InterruptedException {
        String conjunto = "ITQ_paginacao_" + UUID.randomUUID().toString().substring(0, 8);
        LocalDate data = LocalDate.of(2030, 3, 11);

        UUID[] ids = new UUID[3];
        for (int i = 0; i < 3; i++) {
            ExecucaoCurva execucao = ExecucaoCurva.iniciar(
                    UUID.randomUUID(), null, null, conjunto, data, MomentoCurva.INTRADIA,
                    TipoDisparo.MANUAL, "op", Faixa.PRIORITARIA, null, null);
            repository.inserir(execucao);
            // Precisa terminar (estado terminal) antes da próxima, senão colide com o índice
            // único real ux_execucao_curva_ativa_conjunto (só uma ativa por conjunto/data/momento).
            execucao.iniciarExecucao();
            execucao.marcarSemDado("motivo de teste");
            repository.atualizar(execucao);
            ids[i] = execucao.id();
            Thread.sleep(10); // garante iniciado_em estritamente crescente entre as 3 linhas
        }

        PaginaExecucoes pagina1 = repository.buscarComFiltros(conjunto, null, null, 0, 2);
        assertThat(pagina1.totalElementos()).isEqualTo(3);
        assertThat(pagina1.itens()).hasSize(2);
        // A mais recente (ids[2]) deve vir primeiro
        assertThat(pagina1.itens().get(0).id()).isEqualTo(ids[2]);
        assertThat(pagina1.itens().get(1).id()).isEqualTo(ids[1]);

        PaginaExecucoes pagina2 = repository.buscarComFiltros(conjunto, null, null, 1, 2);
        assertThat(pagina2.totalElementos()).isEqualTo(3);
        assertThat(pagina2.itens()).hasSize(1);
        assertThat(pagina2.itens().get(0).id()).isEqualTo(ids[0]);
    }
}
