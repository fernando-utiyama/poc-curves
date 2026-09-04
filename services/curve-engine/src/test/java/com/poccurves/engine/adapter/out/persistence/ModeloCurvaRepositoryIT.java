package com.poccurves.engine.adapter.out.persistence;
import com.poccurves.engine.application.model.EstadoModelo;
import com.poccurves.engine.application.model.ModeloCurva;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Teste de integração real: exige o ambiente local Podman de pé (deploy/podman/up.sh --lite),
 * com o SQL Server já migrado até V18.
 */
class ModeloCurvaRepositoryIT {

    private static JdbcTemplate jdbcTemplateApp() {
        DriverManagerDataSource ds = new DriverManagerDataSource(
                "jdbc:sqlserver://localhost:1433;databaseName=curvasdb;trustServerCertificate=true;encrypt=true",
                "curve_engine_app", "CurveEngineP0c!Local");
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

    private final ModeloCurvaRepository repository = new ModeloCurvaRepository(jdbcTemplateApp());
    private String codigoBuiltin;
    private String codigoGroovy;

    @AfterEach
    void limpar() {
        JdbcTemplate sa = jdbcTemplateSa();
        sa.update("DELETE FROM modelo_curva WHERE codigo IN (?, ?)", codigoBuiltin, codigoGroovy);
    }

    @Test
    void fluxoCompletoBuiltinEGroovyInserirBuscarAtivosDesabilitar() {
        codigoBuiltin = "IT_BUILTIN_" + UUID.randomUUID().toString().substring(0, 8);
        codigoGroovy = "IT_GROOVY_" + UUID.randomUUID().toString().substring(0, 8);

        ModeloCurva builtin = ModeloCurva.builtin(codigoBuiltin, "Modelo embutido de teste");
        ModeloCurva groovy = ModeloCurva.importarGroovy(codigoGroovy, "Modelo Groovy de teste", "def x = 1", "abc123checksum", "teste");

        repository.inserir(builtin);
        repository.inserir(groovy);

        Optional<ModeloCurva> builtinLido = repository.buscarPorCodigo(codigoBuiltin);
        assertThat(builtinLido).isPresent();
        assertThat(builtinLido.get().id()).isEqualTo(builtin.id());
        assertThat(builtinLido.get().tipo().name()).isEqualTo("BUILTIN");
        assertThat(builtinLido.get().estado()).isEqualTo(EstadoModelo.ATIVO);
        assertThat(builtinLido.get().codigoFonte()).isNull();

        Optional<ModeloCurva> groovyLido = repository.buscarPorCodigo(codigoGroovy);
        assertThat(groovyLido).isPresent();
        assertThat(groovyLido.get().tipo().name()).isEqualTo("GROOVY");
        assertThat(groovyLido.get().codigoFonte()).isEqualTo("def x = 1");
        assertThat(groovyLido.get().checksum()).isEqualTo("abc123checksum");
        assertThat(groovyLido.get().importadoPor()).isEqualTo("teste");
        assertThat(groovyLido.get().importadoEm()).isNotNull();

        List<ModeloCurva> ativos = repository.listarAtivos();
        assertThat(ativos).extracting(ModeloCurva::codigo).contains(codigoBuiltin, codigoGroovy);

        builtin.desabilitar();
        repository.atualizar(builtin);

        Optional<ModeloCurva> desabilitado = repository.buscarPorCodigo(codigoBuiltin);
        assertThat(desabilitado).isPresent();
        assertThat(desabilitado.get().estado()).isEqualTo(EstadoModelo.DESABILITADO);

        List<ModeloCurva> ativosDepois = repository.listarAtivos();
        assertThat(ativosDepois).extracting(ModeloCurva::codigo).doesNotContain(codigoBuiltin);
    }
}
