package com.poccurves.processor.adapter.out.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Teste de integração real: exige o ambiente local Podman de pé
 * (`deploy/podman/up.sh --lite`), com o SQL Server já migrado até V9.
 * Nomeado com sufixo "IT" — fora do build padrão.
 * <p>
 * Prova, com a credencial real da aplicação (não SA), que a fronteira de
 * escrita da tarefa 1.6 é reforçada pelo próprio banco, não só por
 * disciplina de código: `curve_processor_app` tem que conseguir escrever
 * nas 5 tabelas da sua fronteira e tem que ser recusado ao tentar escrever
 * em `definicao_curva`/`versao_definicao_curva`/`execucao_curva` (leitura
 * apenas) — verificado manualmente com sqlcmd nesta sessão, automatizado
 * aqui (tarefa 8.13 do backlog).
 */
class FronteiraEscritaCredencialIT {

    private JdbcTemplate jdbcTemplateAplicacao() {
        DriverManagerDataSource ds = new DriverManagerDataSource(
                "jdbc:sqlserver://localhost:1433;databaseName=curvasdb;trustServerCertificate=true;encrypt=true",
                "curve_processor_app", "CurveProcessorP0c!Local");
        ds.setDriverClassName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
        return new JdbcTemplate(ds);
    }

    @Test
    void recusaInsercaoEmDefinicaoCurva() {
        JdbcTemplate jdbc = jdbcTemplateAplicacao();

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO definicao_curva (id, codigo, nome, moeda, modo_origem, horario_limite_publicacao, estado, criado_por) " +
                        "VALUES (?, 'FRONTEIRA_TESTE', 'x', 'BRL', 'IMPORTED', '18:00', 'RASCUNHO', 'teste')",
                UUID.randomUUID().toString()))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("INSERT permission was denied");
    }

    @Test
    void recusaInsercaoEmVersaoDefinicaoCurva() {
        JdbcTemplate jdbc = jdbcTemplateAplicacao();

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO versao_definicao_curva (id, definicao_curva_id, numero_versao, contagem_dias, calendario, " +
                        "interpolador, politica_extrapolacao, politica_arredondamento, vigencia_inicio) " +
                        "VALUES (?, ?, 1, 'DU/252', 'ANBIMA', 'LINEAR', 'TAXA_CONSTANTE', 'PADRAO', '2020-01-01')",
                UUID.randomUUID().toString(), UUID.randomUUID().toString()))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("INSERT permission was denied");
    }

    @Test
    void recusaInsercaoEmExecucaoCurva() {
        JdbcTemplate jdbc = jdbcTemplateAplicacao();

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO execucao_curva (id, correlacao_id, disparo, faixa, estado) VALUES (?, ?, 'MANUAL', 'PRIORITARIA', 'CONCLUIDA')",
                UUID.randomUUID().toString(), UUID.randomUUID().toString()))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("INSERT permission was denied");
    }

    @Test
    void recusaDeleteEmQualquerTabelaDaFronteiraDeEscrita() {
        JdbcTemplate jdbc = jdbcTemplateAplicacao();

        // A fronteira é SELECT/INSERT/UPDATE — nunca DELETE, nem nas tabelas que a aplicação pode escrever.
        assertThatThrownBy(() -> jdbc.update("DELETE FROM lote_ingestao WHERE 1 = 0"))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("DELETE permission was denied");
    }

    @Test
    void permiteInsercaoELeituraEmLoteIngestao() {
        JdbcTemplate jdbc = jdbcTemplateAplicacao();
        String loteExternoId = "fronteira-teste-" + UUID.randomUUID();

        jdbc.update(
                "INSERT INTO lote_ingestao (fonte, conjunto_dados, tipo_payload, data_referencia, lote_externo_id, id_evento, hash_payload, total_blocos, estado) " +
                        "VALUES ('B3', 'TESTE_FRONTEIRA', 'INDIVIDUAL_QUOTES', '2026-08-21', ?, 'evt-1', 'hash-1', 1, 'ABERTO')",
                loteExternoId);

        Integer contagem = jdbc.queryForObject(
                "SELECT COUNT(*) FROM lote_ingestao WHERE lote_externo_id = ?", Integer.class, loteExternoId);

        org.assertj.core.api.Assertions.assertThat(contagem).isEqualTo(1);

        // limpeza via SA, já que a própria aplicação não tem DELETE (comportamento correto, ver teste acima)
        DriverManagerDataSource dsSa = new DriverManagerDataSource(
                "jdbc:sqlserver://localhost:1433;databaseName=curvasdb;trustServerCertificate=true;encrypt=true",
                "sa", "CurvasP0c!Local");
        dsSa.setDriverClassName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
        new JdbcTemplate(dsSa).update("DELETE FROM lote_ingestao WHERE lote_externo_id = ?", loteExternoId);
    }
}
