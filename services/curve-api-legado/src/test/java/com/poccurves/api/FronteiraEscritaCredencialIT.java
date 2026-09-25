package com.poccurves.api;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Teste de integração real: exige o ambiente local Podman de pé
 * (`deploy/podman/up.sh --lite`), com o SQL Server já migrado até V12.
 * Nomeado com sufixo "IT" — fora do build padrão (`mvn test` só coleta
 * `**&#47;*Test.java`).
 * <p>
 * Substitui `FronteiraEscritaTest.servicoNaoDeveExecutarInsertOuUpdateEmTabelasDeCurvaPublicada`
 * — encontrado na auditoria desta sessão como um teste falso: ele fazia
 * grep de string no código-fonte, nunca tocava o banco de dados real, e
 * teria passado mesmo com o serviço rodando com a credencial `sa` (que
 * também foi encontrada, real, nesta auditoria — corrigida em
 * `db/migration/V12__curve_api_credencial_restrita.sql`). Este teste prova
 * a fronteira com a credencial `curve_api_app` de verdade, contra o SQL
 * Server real — o mesmo padrão já usado em
 * `FronteiraEscritaCredencialIT` de curve-processor.
 */
class FronteiraEscritaCredencialIT {

    private JdbcTemplate jdbcTemplateAplicacao() {
        DriverManagerDataSource ds = new DriverManagerDataSource(
                "jdbc:sqlserver://localhost:1433;databaseName=curvasdb;trustServerCertificate=true;encrypt=true",
                "curve_api_app", "CurveApiP0c!Local");
        ds.setDriverClassName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
        return new JdbcTemplate(ds);
    }

    private JdbcTemplate jdbcTemplateSa() {
        DriverManagerDataSource ds = new DriverManagerDataSource(
                "jdbc:sqlserver://localhost:1433;databaseName=curvasdb;trustServerCertificate=true;encrypt=true",
                "sa", "CurvasP0c!Local");
        ds.setDriverClassName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
        return new JdbcTemplate(ds);
    }

    @Test
    void permiteInsercaoELeituraEmDefinicaoCurva() {
        JdbcTemplate jdbc = jdbcTemplateAplicacao();
        String codigo = "FRONTEIRA_API_" + UUID.randomUUID().toString().substring(0, 8);
        String id = UUID.randomUUID().toString();

        jdbc.update(
                "INSERT INTO definicao_curva (id, codigo, nome, moeda, modo_origem, horario_limite_publicacao, estado, criado_por) " +
                        "VALUES (?, ?, 'teste fronteira', 'BRL', 'IMPORTED', '18:00', 'RASCUNHO', 'teste')",
                id, codigo);

        Integer contagem = jdbc.queryForObject(
                "SELECT COUNT(*) FROM definicao_curva WHERE codigo = ?", Integer.class, codigo);
        assertThat(contagem).isEqualTo(1);

        jdbc.update("UPDATE definicao_curva SET nome = 'renomeado' WHERE id = ?", id);

        jdbcTemplateSa().update("DELETE FROM definicao_curva WHERE id = ?", id);
    }

    @Test
    void recusaInsercaoEmVersaoCurva() {
        JdbcTemplate jdbc = jdbcTemplateAplicacao();

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO versao_curva (id, definicao_curva_id, numero_versao, estado) VALUES (?, ?, 1, 'PUBLICADA')",
                UUID.randomUUID().toString(), UUID.randomUUID().toString()))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("INSERT permission was denied");
    }

    @Test
    void recusaInsercaoEmVerticeCurva() {
        JdbcTemplate jdbc = jdbcTemplateAplicacao();

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO vertice_curva (id, versao_curva_id, prazo_dias_uteis, taxa) VALUES (?, ?, 1, 0.1)",
                UUID.randomUUID().toString(), UUID.randomUUID().toString()))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("INSERT permission was denied");
    }

    @Test
    void recusaInsercaoEmProcedenciaCurva() {
        JdbcTemplate jdbc = jdbcTemplateAplicacao();

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO procedencia_curva (id, versao_curva_id, execucao_curva_id, numero_versao_definicao) VALUES (?, ?, ?, 1)",
                UUID.randomUUID().toString(), UUID.randomUUID().toString(), UUID.randomUUID().toString()))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("INSERT permission was denied");
    }

    @Test
    void recusaInsercaoEmModeloCurva() {
        JdbcTemplate jdbc = jdbcTemplateAplicacao();

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO modelo_curva (id, codigo, nome, tipo, estado) VALUES (?, 'X', 'x', 'BUILTIN', 'ATIVO')",
                UUID.randomUUID().toString()))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("INSERT permission was denied");
    }

    @Test
    void recusaDeleteEmQualquerTabelaDaFronteiraDeEscrita() {
        JdbcTemplate jdbc = jdbcTemplateAplicacao();

        assertThatThrownBy(() -> jdbc.update("DELETE FROM definicao_curva WHERE 1 = 0"))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("DELETE permission was denied");
    }
}
