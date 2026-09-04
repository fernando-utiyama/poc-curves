package com.poccurves.orchestrator.adapter.out.persistence;

import tools.jackson.databind.ObjectMapper;
import com.poccurves.orchestrator.domain.DefinicaoConsumidora;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Teste de integração real: exige o SQL Server local de pé, migrado até V15.
 * Verifica {@link DefinicaoCurvaConsultaRepository} de verdade contra o schema real
 * de definicao_curva/versao_definicao_curva.
 */
class DefinicaoCurvaConsultaRepositoryIT {

    private static JdbcTemplate jdbcTemplateSa() {
        DriverManagerDataSource ds = new DriverManagerDataSource(
                "jdbc:sqlserver://localhost:1433;databaseName=curvasdb;trustServerCertificate=true;encrypt=true",
                "sa", "CurvasP0c!Local");
        ds.setDriverClassName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
        return new JdbcTemplate(ds);
    }

    private final DefinicaoCurvaConsultaRepository repository =
            new DefinicaoCurvaConsultaRepository(jdbcTemplateSa(), new ObjectMapper());

    @AfterEach
    void limpar() {
        JdbcTemplate sa = jdbcTemplateSa();
        sa.update("DELETE FROM versao_definicao_curva WHERE definicao_curva_id IN (SELECT id FROM definicao_curva WHERE codigo LIKE 'ITE_%')");
        sa.update("DELETE FROM definicao_curva WHERE codigo LIKE 'ITE_%'");
    }

    private UUID semearDefinicao(JdbcTemplate sa, String codigo, String modoOrigem, String estado, String horarioLimite) {
        UUID id = UUID.randomUUID();
        sa.update(
                "INSERT INTO definicao_curva (id, codigo, nome, moeda, modo_origem, horario_limite_publicacao, estado, criado_por) " +
                        "VALUES (?, ?, ?, 'BRL', ?, ?, ?, 'teste-encadeamento')",
                id.toString(), codigo, codigo, modoOrigem, horarioLimite, estado);
        return id;
    }

    private void semearVersao(JdbcTemplate sa, UUID definicaoId, int numeroVersao, String vinculosFonteJson,
                               LocalDate vigenciaInicio, LocalDate vigenciaFim) {
        sa.update(
                "INSERT INTO versao_definicao_curva (id, definicao_curva_id, numero_versao, contagem_dias, calendario, " +
                        "interpolador, politica_extrapolacao, politica_arredondamento, vinculos_fonte, vigencia_inicio, vigencia_fim) " +
                        "VALUES (?, ?, ?, 'DU/252', 'ANBIMA', 'LINEAR', 'TAXA_CONSTANTE', 'PADRAO', ?, ?, ?)",
                UUID.randomUUID().toString(), definicaoId.toString(), numeroVersao, vinculosFonteJson,
                vigenciaInicio, vigenciaFim);
    }

    @Test
    void resolveApenasDefinicoesBootstrappedAtivasQueConsomemOConjunto() {
        JdbcTemplate sa = jdbcTemplateSa();
        LocalDate hoje = LocalDate.of(2030, 5, 20);
        String conjunto = "ITE_CONJUNTO_" + UUID.randomUUID().toString().substring(0, 8);

        // Consumidora real: BOOTSTRAPPED, ATIVA, vincula o conjunto
        UUID consumidoraId = semearDefinicao(sa, "ITE_CURVA_OK_" + UUID.randomUUID().toString().substring(0, 6),
                "BOOTSTRAPPED", "ATIVA", "18:30");
        semearVersao(sa, consumidoraId, 1, "[\"" + conjunto + "\", \"OUTRO_CONJUNTO\"]",
                LocalDate.of(2020, 1, 1), null);

        // Não deve aparecer: modo IMPORTED
        UUID importadaId = semearDefinicao(sa, "ITE_CURVA_IMPORTED_" + UUID.randomUUID().toString().substring(0, 6),
                "IMPORTED", "ATIVA", "18:30");
        semearVersao(sa, importadaId, 1, "[\"" + conjunto + "\"]", LocalDate.of(2020, 1, 1), null);

        // Não deve aparecer: estado RASCUNHO
        UUID rascunhoId = semearDefinicao(sa, "ITE_CURVA_RASCUNHO_" + UUID.randomUUID().toString().substring(0, 6),
                "BOOTSTRAPPED", "RASCUNHO", "18:30");
        semearVersao(sa, rascunhoId, 1, "[\"" + conjunto + "\"]", LocalDate.of(2020, 1, 1), null);

        // Não deve aparecer: não vincula o conjunto
        UUID outroConjuntoId = semearDefinicao(sa, "ITE_CURVA_OUTRO_" + UUID.randomUUID().toString().substring(0, 6),
                "BOOTSTRAPPED", "ATIVA", "18:30");
        semearVersao(sa, outroConjuntoId, 1, "[\"CONJUNTO_QUE_NAO_E_ESTE\"]", LocalDate.of(2020, 1, 1), null);

        List<DefinicaoConsumidora> resultado =
                repository.buscarDefinicoesBootstrappedQueConsomem(conjunto, hoje);

        assertThat(resultado).hasSize(1);
        assertThat(resultado.get(0).definicaoCurvaId()).isEqualTo(consumidoraId);
        assertThat(resultado.get(0).horarioLimitePublicacao()).isEqualTo(java.time.LocalTime.of(18, 30));
    }

    @Test
    void resolveApenasAVersaoVigenteNaDataQuandoHaMaisDeUma() {
        JdbcTemplate sa = jdbcTemplateSa();
        String conjunto = "ITE_VERSIONADO_" + UUID.randomUUID().toString().substring(0, 8);
        UUID definicaoId = semearDefinicao(sa, "ITE_CURVA_VERSIONADA_" + UUID.randomUUID().toString().substring(0, 6),
                "BOOTSTRAPPED", "ATIVA", "17:00");

        // Versão 1: vigente até 2025-12-31, NÃO vincula o conjunto
        semearVersao(sa, definicaoId, 1, "[\"CONJUNTO_ANTIGO\"]", LocalDate.of(2020, 1, 1), LocalDate.of(2025, 12, 31));
        // Versão 2: vigente a partir de 2026-01-01, vincula o conjunto
        semearVersao(sa, definicaoId, 2, "[\"" + conjunto + "\"]", LocalDate.of(2026, 1, 1), null);

        // Na data de 2024 (vigência da v1), não deve resolver pois v1 não vincula o conjunto
        assertThat(repository.buscarDefinicoesBootstrappedQueConsomem(conjunto, LocalDate.of(2024, 6, 1))).isEmpty();

        // Na data de 2030 (vigência da v2), deve resolver
        List<DefinicaoConsumidora> resultado =
                repository.buscarDefinicoesBootstrappedQueConsomem(conjunto, LocalDate.of(2030, 6, 1));
        assertThat(resultado).hasSize(1);
        assertThat(resultado.get(0).definicaoCurvaId()).isEqualTo(definicaoId);
    }
}
