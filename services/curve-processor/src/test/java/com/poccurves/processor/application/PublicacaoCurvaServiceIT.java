package com.poccurves.processor.application;
import com.poccurves.processor.domain.cargamanual.ResultadoPublicacaoCarga;
import com.poccurves.processor.domain.curva.CurvaNaoMapeadaException;
import com.poccurves.processor.domain.curva.CurvaVaziaException;
import com.poccurves.processor.domain.curva.EstadoVersaoCurva;
import com.poccurves.processor.domain.curva.IncoerenciaModoOrigemException;
import com.poccurves.processor.domain.curva.MomentoCurva;
import com.poccurves.processor.domain.curva.OrigemVersao;
import com.poccurves.processor.domain.curva.VersaoCurva;
import com.poccurves.processor.domain.curva.VerticeCurva;

import com.poccurves.processor.CurveProcessorApplication;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Teste de integração real: exige o ambiente local Podman de pé
 * (`deploy/podman/up.sh --lite`), com o SQL Server já migrado até V8.
 * Nomeado com sufixo "IT" — fora do build padrão (Surefire só coleta
 * `**&#47;*Test.java`), mesmo padrão de IngestaoServiceIT.
 * <p>
 * Como definicao_curva/versao_definicao_curva estão fora da fronteira de
 * escrita do curve-processor (só SELECT — tarefa 1.6), este teste semeia
 * as linhas de teste via SA (setup/teardown), e só exerce
 * PublicacaoCurvaService com a credencial restrita real da aplicação.
 */
@SpringBootTest(classes = CurveProcessorApplication.class)
class PublicacaoCurvaServiceIT {

    @DynamicPropertySource
    static void datasourceLocal(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",
                () -> "jdbc:sqlserver://localhost:1433;databaseName=curvasdb;trustServerCertificate=true;encrypt=true");
        registry.add("spring.kafka.bootstrap-servers", () -> "localhost:19092");
    }

    @Autowired
    private PublicacaoCurvaService publicacaoCurvaService;

    private final List<UUID> execucoesCriadas = new java.util.ArrayList<>();

    private static JdbcTemplate jdbcTemplateSa() {
        DriverManagerDataSource ds = new DriverManagerDataSource(
                "jdbc:sqlserver://localhost:1433;databaseName=curvasdb;trustServerCertificate=true;encrypt=true",
                "sa", "CurvasP0c!Local");
        ds.setDriverClassName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
        return new JdbcTemplate(ds);
    }

    private static final String CODIGO_IMPORTADA = "IT_CURVA_IMPORTADA";
    private static final String CODIGO_BOOTSTRAPPED = "IT_CURVA_BOOTSTRAPPED";
    private static final String CODIGO_CARREGADA = "IT_CURVA_CARREGADA";

    private UUID semearDefinicao(JdbcTemplate sa, String codigo, String modoOrigem) {
        UUID definicaoId = UUID.randomUUID();
        sa.update(
                "INSERT INTO definicao_curva (id, codigo, nome, moeda, modo_origem, horario_limite_publicacao, estado, criado_por) " +
                        "VALUES (?, ?, ?, 'BRL', ?, '18:00', 'ATIVA', 'teste-it')",
                definicaoId.toString(), codigo, codigo, modoOrigem);

        UUID versaoDefId = UUID.randomUUID();
        sa.update(
                "INSERT INTO versao_definicao_curva (id, definicao_curva_id, numero_versao, contagem_dias, calendario, " +
                        "interpolador, politica_extrapolacao, politica_arredondamento, vigencia_inicio, vigencia_fim) " +
                        "VALUES (?, ?, 1, 'DU/252', 'ANBIMA', 'LINEAR', 'TAXA_CONSTANTE', 'PADRAO', '2020-01-01', NULL)",
                versaoDefId.toString(), definicaoId.toString());

        return definicaoId;
    }

    /** versao_curva.execucao_curva_id tem FK NOT NULL real para execucao_curva — precisa existir antes. */
    private UUID semearExecucao(JdbcTemplate sa) {
        UUID execucaoId = UUID.randomUUID();
        sa.update(
                "INSERT INTO execucao_curva (id, correlacao_id, disparo, faixa, estado) " +
                        "VALUES (?, ?, 'MANUAL', 'PRIORITARIA', 'CONCLUIDA')",
                execucaoId.toString(), UUID.randomUUID().toString());
        execucoesCriadas.add(execucaoId);
        return execucaoId;
    }

    /** procedencia_curva.lote_ingestao_id tem FK real para lote_ingestao — precisa existir antes. */
    private long semearLoteIngestao(JdbcTemplate sa) {
        String loteExternoId = "it-lote-curva-" + UUID.randomUUID();
        return sa.queryForObject(
                "INSERT INTO lote_ingestao (fonte, conjunto_dados, tipo_payload, data_referencia, lote_externo_id, id_evento, hash_payload, total_blocos, estado) " +
                        "OUTPUT INSERTED.id " +
                        "VALUES ('B3', 'CURVA_REFERENCIA', 'READY_CURVE', '2026-08-21', ?, 'evt-it', 'hash-it', 1, 'COMPLETO')",
                Long.class, loteExternoId);
    }

    @AfterEach
    void limpar() {
        JdbcTemplate sa = jdbcTemplateSa();
        List<String> codigos = List.of(CODIGO_IMPORTADA, CODIGO_BOOTSTRAPPED, CODIGO_CARREGADA);
        sa.update("DELETE FROM validacao_curva WHERE versao_curva_id IN (SELECT id FROM versao_curva WHERE definicao_curva_id IN (SELECT id FROM definicao_curva WHERE codigo IN (?, ?, ?)))", codigos.toArray());
        sa.update("DELETE FROM procedencia_curva WHERE versao_curva_id IN (SELECT id FROM versao_curva WHERE definicao_curva_id IN (SELECT id FROM definicao_curva WHERE codigo IN (?, ?, ?)))", codigos.toArray());
        sa.update("DELETE FROM vertice_curva WHERE versao_curva_id IN (SELECT id FROM versao_curva WHERE definicao_curva_id IN (SELECT id FROM definicao_curva WHERE codigo IN (?, ?, ?)))", codigos.toArray());
        sa.update("DELETE FROM versao_curva WHERE definicao_curva_id IN (SELECT id FROM definicao_curva WHERE codigo IN (?, ?, ?))", codigos.toArray());
        sa.update("DELETE FROM versao_definicao_curva WHERE definicao_curva_id IN (SELECT id FROM definicao_curva WHERE codigo IN (?, ?, ?))", codigos.toArray());
        sa.update("DELETE FROM definicao_curva WHERE codigo IN (?, ?, ?)", codigos.toArray());
        sa.update("DELETE FROM lote_ingestao WHERE lote_externo_id LIKE 'it-lote-curva-%'");
        for (UUID execucaoId : execucoesCriadas) {
            sa.update("DELETE FROM execucao_curva WHERE id = ?", execucaoId.toString());
        }
        execucoesCriadas.clear();
    }

    @Test
    void publicaCurvaImportadaMarcaAnteriorComoSubstituidaENumeraIncrementalmente() {
        JdbcTemplate sa = jdbcTemplateSa();
        semearDefinicao(sa, CODIGO_IMPORTADA, "IMPORTED");

        List<VerticeCurva> vertices1 = List.of(
                new VerticeCurva(1, 1, null, new BigDecimal("14.129"), null),
                new VerticeCurva(21, 21, null, new BigDecimal("14.25"), null));

        VersaoCurva v1 = publicacaoCurvaService.publicarCurvaImportada(
                CODIGO_IMPORTADA, LocalDate.of(2026, 8, 21), MomentoCurva.FECHAMENTO,
                semearExecucao(sa), semearLoteIngestao(sa), "ref-1", "hash-1", vertices1);

        assertThat(v1.numeroVersao()).isEqualTo(1);
        assertThat(v1.estado()).isEqualTo(EstadoVersaoCurva.PUBLICADA);
        assertThat(v1.publicadoEm()).isNotNull();

        Integer vertexCount1 = sa.queryForObject("SELECT COUNT(*) FROM vertice_curva WHERE versao_curva_id = ?", Integer.class, v1.id().toString());
        assertThat(vertexCount1).isEqualTo(2);

        // Segunda publicação para a MESMA chave (definição, data, momento): a v1 deve virar SUBSTITUIDA
        List<VerticeCurva> vertices2 = List.of(new VerticeCurva(1, 1, null, new BigDecimal("14.200"), null));

        VersaoCurva v2 = publicacaoCurvaService.publicarCurvaImportada(
                CODIGO_IMPORTADA, LocalDate.of(2026, 8, 21), MomentoCurva.FECHAMENTO,
                semearExecucao(sa), semearLoteIngestao(sa), "ref-2", "hash-2", vertices2);

        assertThat(v2.numeroVersao()).isEqualTo(2);
        assertThat(v2.estado()).isEqualTo(EstadoVersaoCurva.PUBLICADA);

        String estadoV1DepoisDaSegunda = sa.queryForObject("SELECT estado FROM versao_curva WHERE id = ?", String.class, v1.id().toString());
        assertThat(estadoV1DepoisDaSegunda).isEqualTo("SUBSTITUIDA");
    }

    @Test
    void publicaVerticesIdenticosAosRecebidosDigitoADigito() {
        JdbcTemplate sa = jdbcTemplateSa();
        semearDefinicao(sa, CODIGO_IMPORTADA, "IMPORTED");

        // Taxa real com 12 casas decimais, prazo com dias corridos e vencimento
        // preenchidos — exercita todos os campos de VerticeCurva de uma vez.
        VerticeCurva verticeEnviado = new VerticeCurva(
                21, 25, LocalDate.of(2026, 9, 15),
                new BigDecimal("14.129384756123"), new BigDecimal("0.998877665544"));

        VersaoCurva versao = publicacaoCurvaService.publicarCurvaImportada(
                CODIGO_IMPORTADA, LocalDate.of(2026, 8, 21), MomentoCurva.FECHAMENTO,
                semearExecucao(sa), semearLoteIngestao(sa), "ref", "hash",
                List.of(verticeEnviado));

        var linha = sa.queryForMap(
                "SELECT prazo_dias_uteis, prazo_dias_corridos, data_vencimento, taxa, fator_desconto FROM vertice_curva WHERE versao_curva_id = ?",
                versao.id().toString());

        assertThat(linha.get("prazo_dias_uteis")).isEqualTo(verticeEnviado.prazoDiasUteis());
        assertThat(linha.get("prazo_dias_corridos")).isEqualTo(verticeEnviado.prazoDiasCorridos());
        assertThat(((java.sql.Date) linha.get("data_vencimento")).toLocalDate()).isEqualTo(verticeEnviado.dataVencimento());
        assertThat((BigDecimal) linha.get("taxa")).isEqualByComparingTo(verticeEnviado.taxa());
        assertThat(((BigDecimal) linha.get("taxa")).toPlainString()).contains("14.129384756123");
        assertThat((BigDecimal) linha.get("fator_desconto")).isEqualByComparingTo(verticeEnviado.fatorDesconto());
    }

    @Test
    void gravaProcedenciaComLoteEExecucao() {
        JdbcTemplate sa = jdbcTemplateSa();
        semearDefinicao(sa, CODIGO_IMPORTADA, "IMPORTED");
        UUID execucaoCurvaId = semearExecucao(sa);
        long loteIngestaoId = semearLoteIngestao(sa);

        VersaoCurva versao = publicacaoCurvaService.publicarCurvaImportada(
                CODIGO_IMPORTADA, LocalDate.of(2026, 8, 21), MomentoCurva.ABERTURA,
                execucaoCurvaId, loteIngestaoId, "ref-x", "hash-x",
                List.of(new VerticeCurva(1, 1, null, new BigDecimal("14.0"), null)));

        String execucaoGravada = sa.queryForObject(
                "SELECT execucao_curva_id FROM procedencia_curva WHERE versao_curva_id = ?", String.class, versao.id().toString());
        Long loteGravado = sa.queryForObject(
                "SELECT lote_ingestao_id FROM procedencia_curva WHERE versao_curva_id = ?", Long.class, versao.id().toString());

        assertThat(UUID.fromString(execucaoGravada)).isEqualTo(execucaoCurvaId);
        assertThat(loteGravado).isEqualTo(loteIngestaoId);
    }

    @Test
    void recusaCurvaNaoMapeada() {
        assertThatThrownBy(() -> publicacaoCurvaService.publicarCurvaImportada(
                "CODIGO_QUE_NAO_EXISTE", LocalDate.now(), MomentoCurva.FECHAMENTO,
                UUID.randomUUID(), 1L, "ref", "hash",
                List.of(new VerticeCurva(1, null, null, new BigDecimal("1"), null))))
                .isInstanceOf(CurvaNaoMapeadaException.class);
    }

    @Test
    void recusaDefinicaoBootstrapped() {
        JdbcTemplate sa = jdbcTemplateSa();
        semearDefinicao(sa, CODIGO_BOOTSTRAPPED, "BOOTSTRAPPED");

        assertThatThrownBy(() -> publicacaoCurvaService.publicarCurvaImportada(
                CODIGO_BOOTSTRAPPED, LocalDate.now(), MomentoCurva.FECHAMENTO,
                UUID.randomUUID(), 1L, "ref", "hash",
                List.of(new VerticeCurva(1, null, null, new BigDecimal("1"), null))))
                .isInstanceOf(IncoerenciaModoOrigemException.class);
    }

    @Test
    void recusaCurvaVazia() {
        JdbcTemplate sa = jdbcTemplateSa();
        semearDefinicao(sa, CODIGO_IMPORTADA, "IMPORTED");

        assertThatThrownBy(() -> publicacaoCurvaService.publicarCurvaImportada(
                CODIGO_IMPORTADA, LocalDate.now(), MomentoCurva.FECHAMENTO,
                UUID.randomUUID(), 1L, "ref", "hash", List.of()))
                .isInstanceOf(CurvaVaziaException.class);
    }

    @Test
    void publicaCargaManualAprovadaGravaValidacaoEProcedenciaDeCarga() {
        JdbcTemplate sa = jdbcTemplateSa();
        semearDefinicao(sa, CODIGO_CARREGADA, "IMPORTED");

        ResultadoPublicacaoCarga resultado = publicacaoCurvaService.publicarCurvaCarregada(
                CODIGO_CARREGADA, LocalDate.of(2026, 8, 21), MomentoCurva.FECHAMENTO,
                semearExecucao(sa),
                List.of(new VerticeCurva(1, null, null, new BigDecimal("14.129"), null)),
                "curva-manual.csv", "hash-carga-aprovada-" + UUID.randomUUID(), "operador.risco", "correção de cadastro");

        assertThat(resultado.recarga()).isFalse();
        assertThat(resultado.versao().estado()).isEqualTo(EstadoVersaoCurva.PUBLICADA);
        assertThat(resultado.versao().origemVersao()).isEqualTo(OrigemVersao.CARREGADA);
        assertThat(resultado.resultadosValidacao()).isNotEmpty();

        Integer validacoesGravadas = sa.queryForObject(
                "SELECT COUNT(*) FROM validacao_curva WHERE versao_curva_id = ?", Integer.class, resultado.versao().id().toString());
        assertThat(validacoesGravadas).isEqualTo(resultado.resultadosValidacao().size());

        String carregadoPor = sa.queryForObject(
                "SELECT carregado_por FROM procedencia_curva WHERE versao_curva_id = ?", String.class, resultado.versao().id().toString());
        assertThat(carregadoPor).isEqualTo("operador.risco");
    }

    @Test
    void reprovaCargaManualComTaxaNegativaEPreservaVersaoAnteriorPublicada() {
        JdbcTemplate sa = jdbcTemplateSa();
        semearDefinicao(sa, CODIGO_CARREGADA, "IMPORTED");

        ResultadoPublicacaoCarga v1 = publicacaoCurvaService.publicarCurvaCarregada(
                CODIGO_CARREGADA, LocalDate.of(2026, 8, 21), MomentoCurva.FECHAMENTO,
                semearExecucao(sa),
                List.of(new VerticeCurva(1, null, null, new BigDecimal("14.129"), null)),
                "curva-v1.csv", "hash-v1-" + UUID.randomUUID(), "operador.risco", "carga inicial");
        assertThat(v1.versao().estado()).isEqualTo(EstadoVersaoCurva.PUBLICADA);

        ResultadoPublicacaoCarga v2 = publicacaoCurvaService.publicarCurvaCarregada(
                CODIGO_CARREGADA, LocalDate.of(2026, 8, 21), MomentoCurva.FECHAMENTO,
                semearExecucao(sa),
                List.of(new VerticeCurva(1, null, null, new BigDecimal("-1"), null)),
                "curva-v2-com-erro.csv", "hash-v2-" + UUID.randomUUID(), "operador.risco", "tentativa com erro");

        assertThat(v2.versao().estado()).isEqualTo(EstadoVersaoCurva.REPROVADA);

        // A v1 PUBLICADA continua vigente — nunca foi substituída por uma versão reprovada
        String estadoV1 = sa.queryForObject("SELECT estado FROM versao_curva WHERE id = ?", String.class, v1.versao().id().toString());
        assertThat(estadoV1).isEqualTo("PUBLICADA");
    }

    @Test
    void reconheceRecargaDoMesmoArquivoPelaHashSemDuplicarVersao() {
        JdbcTemplate sa = jdbcTemplateSa();
        semearDefinicao(sa, CODIGO_CARREGADA, "IMPORTED");
        String hashCompartilhado = "hash-recarga-" + UUID.randomUUID();

        ResultadoPublicacaoCarga primeira = publicacaoCurvaService.publicarCurvaCarregada(
                CODIGO_CARREGADA, LocalDate.of(2026, 8, 21), MomentoCurva.FECHAMENTO,
                semearExecucao(sa),
                List.of(new VerticeCurva(1, null, null, new BigDecimal("14.129"), null)),
                "curva.csv", hashCompartilhado, "operador.risco", "carga original");
        assertThat(primeira.recarga()).isFalse();

        ResultadoPublicacaoCarga recarga = publicacaoCurvaService.publicarCurvaCarregada(
                CODIGO_CARREGADA, LocalDate.of(2026, 8, 21), MomentoCurva.FECHAMENTO,
                semearExecucao(sa),
                List.of(new VerticeCurva(1, null, null, new BigDecimal("14.129"), null)),
                "curva.csv", hashCompartilhado, "operador.risco", "reenvio do mesmo arquivo");

        assertThat(recarga.recarga()).isTrue();
        assertThat(recarga.versao().id()).isEqualTo(primeira.versao().id());

        Integer totalVersoes = sa.queryForObject(
                "SELECT COUNT(*) FROM versao_curva WHERE definicao_curva_id = (SELECT id FROM definicao_curva WHERE codigo = ?)",
                Integer.class, CODIGO_CARREGADA);
        assertThat(totalVersoes).isEqualTo(1);
    }
}
