package com.poccurves.engine.adapter.out.persistence;
import com.poccurves.engine.domain.versao.DefinicaoResolvida;

import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.sql.Date;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DefinicaoCurvaResolutionRepositoryIT {

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

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final DefinicaoCurvaResolutionRepository repository = new DefinicaoCurvaResolutionRepository(jdbcTemplateApp(), objectMapper);

    private UUID definicaoId;
    private UUID versaoVigenteId;
    private UUID versaoPassadaId;
    private UUID versaoFuturaId;
    private String codigo;

    @AfterEach
    void limpar() {
        if (codigo != null) {
            JdbcTemplate sa = jdbcTemplateSa();
            sa.update("DELETE FROM versao_definicao_curva WHERE definicao_curva_id = ?", definicaoId);
            sa.update("DELETE FROM definicao_curva WHERE id = ?", definicaoId);
        }
    }

    @Test
    void resolverVigente() {
        codigo = "IT_DEF_" + UUID.randomUUID().toString().substring(0, 8);
        definicaoId = UUID.randomUUID();
        versaoVigenteId = UUID.randomUUID();
        versaoPassadaId = UUID.randomUUID();
        versaoFuturaId = UUID.randomUUID();

        JdbcTemplate sa = jdbcTemplateSa();
        
        sa.update("""
                INSERT INTO definicao_curva (id, codigo, nome, moeda, modo_origem, horario_limite_publicacao, estado, criado_por)
                VALUES (?, ?, 'Def Teste', 'BRL', 'BOOTSTRAPPED', '18:00:00', 'ATIVA', 'teste')
                """, definicaoId, codigo);
                
        // Passada
        sa.update("""
                INSERT INTO versao_definicao_curva (id, definicao_curva_id, numero_versao, contagem_dias, calendario, interpolador, politica_extrapolacao, politica_arredondamento, vinculos_fonte, vigencia_inicio, vigencia_fim)
                VALUES (?, ?, 1, 'UTEIS', 'BR_B3', 'FLAT', 'FLAT', 'HALF_UP', '["BVBG.086","BVBG.028"]', ?, ?)
                """, versaoPassadaId, definicaoId, Date.valueOf(LocalDate.now().minusDays(10)), Date.valueOf(LocalDate.now().minusDays(1)));
                
        // Vigente
        sa.update("""
                INSERT INTO versao_definicao_curva (id, definicao_curva_id, numero_versao, contagem_dias, calendario, interpolador, politica_extrapolacao, politica_arredondamento, vinculos_fonte, limites_validacao, vigencia_inicio, vigencia_fim)
                VALUES (?, ?, 2, 'UTEIS', 'BR_B3', 'FLAT', 'FLAT', 'HALF_UP', '["BVBG.086","BVBG.028"]', '[{"teste":"ESTRUTURAL","classificacao":"BLOQUEANTE","limite":"3"}]', ?, NULL)
                """, versaoVigenteId, definicaoId, Date.valueOf(LocalDate.now()));

        // Futura
        sa.update("""
                INSERT INTO versao_definicao_curva (id, definicao_curva_id, numero_versao, contagem_dias, calendario, interpolador, politica_extrapolacao, politica_arredondamento, vinculos_fonte, limites_validacao, vigencia_inicio, vigencia_fim)
                VALUES (?, ?, 3, 'UTEIS', 'BR_B3', 'FLAT', 'FLAT', 'HALF_UP', '["BVBG.086","BVBG.028"]', NULL, ?, NULL)
                """, versaoFuturaId, definicaoId, Date.valueOf(LocalDate.now().plusDays(10)));

        Optional<DefinicaoResolvida> vigenteOpt = repository.resolverVigente(codigo, LocalDate.now());
        
        assertThat(vigenteOpt).isPresent();
        DefinicaoResolvida vigente = vigenteOpt.get();
        
        assertThat(vigente.definicaoCurvaId()).isEqualTo(definicaoId);
        assertThat(vigente.codigo()).isEqualTo(codigo);
        assertThat(vigente.modoOrigem()).isEqualTo("BOOTSTRAPPED");
        assertThat(vigente.versaoDefinicaoCurvaId()).isEqualTo(versaoVigenteId);
        assertThat(vigente.numeroVersaoDefinicao()).isEqualTo(2);
        assertThat(vigente.vinculosFonte()).containsExactly("BVBG.086", "BVBG.028");
        assertThat(vigente.dependeDe()).isEmpty();
        
        assertThat(vigente.limitesValidacao()).hasSize(1);
        assertThat(vigente.limitesValidacao().get(0).teste()).isEqualTo("ESTRUTURAL");
        assertThat(vigente.limitesValidacao().get(0).classificacao().name()).isEqualTo("BLOQUEANTE");
        assertThat(vigente.limitesValidacao().get(0).limite()).isEqualByComparingTo("3");
        
        // Verifica que se a data for no passado, acha a v1
        Optional<DefinicaoResolvida> passadaOpt = repository.resolverVigente(codigo, LocalDate.now().minusDays(5));
        assertThat(passadaOpt).isPresent();
        assertThat(passadaOpt.get().numeroVersaoDefinicao()).isEqualTo(1);
    }

    private UUID definicaoImportadaId;
    private String codigoImportada;

    @Test
    void resolverVigenteTrazCodigoCurvaImportadaIrmaoQuandoVinculado() {
        codigo = "IT_DEF_IRMAO_" + UUID.randomUUID().toString().substring(0, 8);
        codigoImportada = "IT_DEF_IMPORTADA_" + UUID.randomUUID().toString().substring(0, 8);
        definicaoId = UUID.randomUUID();
        definicaoImportadaId = UUID.randomUUID();
        versaoVigenteId = UUID.randomUUID();

        JdbcTemplate sa = jdbcTemplateSa();

        sa.update("""
                INSERT INTO definicao_curva (id, codigo, nome, moeda, modo_origem, horario_limite_publicacao, estado, criado_por, codigo_curva_importada_irmao)
                VALUES (?, ?, 'Def Bootstrapped', 'BRL', 'BOOTSTRAPPED', '18:00:00', 'ATIVA', 'teste', ?)
                """, definicaoId, codigo, codigoImportada);

        sa.update("""
                INSERT INTO definicao_curva (id, codigo, nome, moeda, modo_origem, horario_limite_publicacao, estado, criado_por)
                VALUES (?, ?, 'Def Importada', 'BRL', 'IMPORTED', '18:00:00', 'ATIVA', 'teste')
                """, definicaoImportadaId, codigoImportada);

        sa.update("""
                INSERT INTO versao_definicao_curva (id, definicao_curva_id, numero_versao, contagem_dias, calendario, interpolador, politica_extrapolacao, politica_arredondamento, vigencia_inicio, vigencia_fim)
                VALUES (?, ?, 1, 'UTEIS', 'BR_B3', 'FLAT', 'FLAT', 'HALF_UP', ?, NULL)
                """, versaoVigenteId, definicaoId, Date.valueOf(LocalDate.now()));

        try {
            Optional<DefinicaoResolvida> resultado = repository.resolverVigente(codigo, LocalDate.now());

            assertThat(resultado).isPresent();
            assertThat(resultado.get().codigoCurvaImportadaIrmao()).isEqualTo(codigoImportada);

            Optional<UUID> idResolvido = repository.resolverIdPorCodigo(codigoImportada);
            assertThat(idResolvido).contains(definicaoImportadaId);

            assertThat(repository.resolverIdPorCodigo("CODIGO_QUE_NAO_EXISTE_" + UUID.randomUUID())).isEmpty();
        } finally {
            sa.update("DELETE FROM definicao_curva WHERE id = ?", definicaoImportadaId);
        }
    }
}
