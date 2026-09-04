package com.poccurves.processor.application;

import com.poccurves.processor.CurveProcessorApplication;
import com.poccurves.processor.domain.EstadoLoteIngestao;
import com.poccurves.processor.domain.PontoDadoMercado;
import com.poccurves.processor.domain.ResultadoProcessamentoBloco;
import com.poccurves.processor.domain.TipoPayload;
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
 * Testes de integracao reais para processamento multi-bloco de lotes de ingestao:
 * exige o ambiente local Podman de pe (`deploy/podman/up.sh --lite`), com o SQL Server
 * acessivel em localhost:1433 e Kafka em localhost:19092.
 * <p>
 * Sufixo "IT" para ficar fora do build padrao do Maven Surefire (`**&#47;*Test.java`).
 * Cobre tarefas 8.1, 8.4, 8.15, 8.16 e 8.17 do backlog.
 */
@SpringBootTest(classes = CurveProcessorApplication.class)
class IngestaoServiceMultiBlocoIT {

    @DynamicPropertySource
    static void datasourceLocal(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",
                () -> "jdbc:sqlserver://localhost:1433;databaseName=curvasdb;trustServerCertificate=true;encrypt=true");
        registry.add("spring.kafka.bootstrap-servers", () -> "localhost:19092");
    }

    @Autowired
    private IngestaoService ingestaoService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * curve_processor_app nao possui permissao de DELETE (fronteira de escrita restrita).
     * O setup/teardown dos testes utiliza credencial SA para expurgo dos dados criados.
     */
    private static JdbcTemplate jdbcTemplateSa() {
        DriverManagerDataSource ds = new DriverManagerDataSource(
                "jdbc:sqlserver://localhost:1433;databaseName=curvasdb;trustServerCertificate=true;encrypt=true",
                "sa", "CurvasP0c!Local");
        ds.setDriverClassName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
        return new JdbcTemplate(ds);
    }

    private void limparDadosDeTeste() {
        JdbcTemplate sa = jdbcTemplateSa();
        sa.update("DELETE FROM ponto_dado_mercado WHERE chave_instrumento LIKE 'MULTIBLOCO_%'");
        sa.update("DELETE FROM lote_ingestao WHERE lote_externo_id LIKE 'it-multibloco-%'");
    }

    @AfterEach
    void depois() {
        limparDadosDeTeste();
    }

    /**
     * Tarefa 8.4: Consolidacao de lote com multiplos blocos.
     * Envia 3 blocos ordenados (sequencia 1, 2, 3) para o mesmo loteExternoId (totalBlocos=3).
     * Comprova via SQL que o lote consolida para COMPLETO com contadores atualizados
     * e que todos os pontos foram persistidos.
     */
    @Test
    void consolidaLoteComMultiplosBlocos() {
        String loteExternoId = "it-multibloco-" + UUID.randomUUID();
        LocalDate dataRef = LocalDate.of(2026, 8, 21);

        PontoDadoMercado pontoA = new PontoDadoMercado(
                "B3", "BVBG.086", dataRef, "MULTIBLOCO_A",
                new BigDecimal("10.125"), "TAXA_AJUSTE", LocalDate.of(2028, 1, 1));
        PontoDadoMercado pontoB = new PontoDadoMercado(
                "B3", "BVBG.086", dataRef, "MULTIBLOCO_B",
                new BigDecimal("11.250"), "TAXA_AJUSTE", LocalDate.of(2029, 1, 1));
        PontoDadoMercado pontoC = new PontoDadoMercado(
                "B3", "BVBG.086", dataRef, "MULTIBLOCO_C",
                new BigDecimal("12.375"), "TAXA_AJUSTE", LocalDate.of(2030, 1, 1));

        // Bloco 1 de 3 -> Lote permanece ABERTO
        ResultadoProcessamentoBloco r1 = ingestaoService.processarBloco(
                "B3", "BVBG.086", dataRef, loteExternoId,
                UUID.randomUUID(),
                "evt-1", "sha256:" + "a".repeat(64), 1, 3,
                TipoPayload.INDIVIDUAL_QUOTES, List.of(pontoA));
        assertThat(r1.lote().estado()).isEqualTo(EstadoLoteIngestao.ABERTO);
        assertThat(r1.lote().blocosRecebidos()).isEqualTo(1);

        // Bloco 2 de 3 -> Lote permanece ABERTO
        ResultadoProcessamentoBloco r2 = ingestaoService.processarBloco(
                "B3", "BVBG.086", dataRef, loteExternoId,
                UUID.randomUUID(),
                "evt-2", "sha256:" + "b".repeat(64), 2, 3,
                TipoPayload.INDIVIDUAL_QUOTES, List.of(pontoB));
        assertThat(r2.lote().estado()).isEqualTo(EstadoLoteIngestao.ABERTO);
        assertThat(r2.lote().blocosRecebidos()).isEqualTo(2);

        // Bloco 3 de 3 -> Lote consolida para COMPLETO
        ResultadoProcessamentoBloco r3 = ingestaoService.processarBloco(
                "B3", "BVBG.086", dataRef, loteExternoId,
                UUID.randomUUID(),
                "evt-3", "sha256:" + "c".repeat(64), 3, 3,
                TipoPayload.INDIVIDUAL_QUOTES, List.of(pontoC));
        assertThat(r3.lote().estado()).isEqualTo(EstadoLoteIngestao.COMPLETO);
        assertThat(r3.lote().blocosRecebidos()).isEqualTo(3);
        assertThat(r3.lote().pontosGravados()).isEqualTo(3);

        // Verificacoes diretas no banco de dados via SQL
        String estadoBanco = jdbcTemplate.queryForObject(
                "SELECT estado FROM lote_ingestao WHERE lote_externo_id = ?",
                String.class, loteExternoId);
        Integer blocosBanco = jdbcTemplate.queryForObject(
                "SELECT blocos_recebidos FROM lote_ingestao WHERE lote_externo_id = ?",
                Integer.class, loteExternoId);
        Integer pontosGravadosBanco = jdbcTemplate.queryForObject(
                "SELECT pontos_gravados FROM lote_ingestao WHERE lote_externo_id = ?",
                Integer.class, loteExternoId);
        Integer contagemPontos = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ponto_dado_mercado WHERE chave_instrumento IN ('MULTIBLOCO_A', 'MULTIBLOCO_B', 'MULTIBLOCO_C')",
                Integer.class);

        assertThat(estadoBanco).isEqualTo("COMPLETO");
        assertThat(blocosBanco).isEqualTo(3);
        assertThat(pontosGravadosBanco).isEqualTo(3);
        assertThat(contagemPontos).isEqualTo(3);
    }

    /**
     * Tarefa 8.15: Blocos fora de ordem.
     * Envia os blocos na ordem de sequencia 2, 1, 3 para o mesmo loteExternoId (totalBlocos=3).
     * Comprova que a consolidacao depende apenas da contagem de blocos e consolida para COMPLETO.
     */
    @Test
    void consolidaLoteComBlocosForaDeOrdem() {
        String loteExternoId = "it-multibloco-" + UUID.randomUUID();
        LocalDate dataRef = LocalDate.of(2026, 8, 21);

        PontoDadoMercado pontoA = new PontoDadoMercado(
                "B3", "BVBG.086", dataRef, "MULTIBLOCO_ORDEM_A",
                new BigDecimal("10.000"), "TAXA_AJUSTE", null);
        PontoDadoMercado pontoB = new PontoDadoMercado(
                "B3", "BVBG.086", dataRef, "MULTIBLOCO_ORDEM_B",
                new BigDecimal("11.000"), "TAXA_AJUSTE", null);
        PontoDadoMercado pontoC = new PontoDadoMercado(
                "B3", "BVBG.086", dataRef, "MULTIBLOCO_ORDEM_C",
                new BigDecimal("12.000"), "TAXA_AJUSTE", null);

        // 1a chamada: envia sequencia 2
        ingestaoService.processarBloco(
                "B3", "BVBG.086", dataRef, loteExternoId,
                UUID.randomUUID(),
                "evt-2", "sha256:" + "2".repeat(64), 2, 3,
                TipoPayload.INDIVIDUAL_QUOTES, List.of(pontoA));

        // 2a chamada: envia sequencia 1
        ingestaoService.processarBloco(
                "B3", "BVBG.086", dataRef, loteExternoId,
                UUID.randomUUID(),
                "evt-1", "sha256:" + "1".repeat(64), 1, 3,
                TipoPayload.INDIVIDUAL_QUOTES, List.of(pontoB));

        // 3a chamada: envia sequencia 3
        ResultadoProcessamentoBloco r3 = ingestaoService.processarBloco(
                "B3", "BVBG.086", dataRef, loteExternoId,
                UUID.randomUUID(),
                "evt-3", "sha256:" + "3".repeat(64), 3, 3,
                TipoPayload.INDIVIDUAL_QUOTES, List.of(pontoC));

        assertThat(r3.lote().estado()).isEqualTo(EstadoLoteIngestao.COMPLETO);
        assertThat(r3.lote().blocosRecebidos()).isEqualTo(3);

        // Verificacoes diretas no banco de dados via SQL
        String estadoBanco = jdbcTemplate.queryForObject(
                "SELECT estado FROM lote_ingestao WHERE lote_externo_id = ?",
                String.class, loteExternoId);
        Integer blocosBanco = jdbcTemplate.queryForObject(
                "SELECT blocos_recebidos FROM lote_ingestao WHERE lote_externo_id = ?",
                Integer.class, loteExternoId);
        Integer contagemPontos = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ponto_dado_mercado WHERE chave_instrumento IN ('MULTIBLOCO_ORDEM_A', 'MULTIBLOCO_ORDEM_B', 'MULTIBLOCO_ORDEM_C')",
                Integer.class);

        assertThat(estadoBanco).isEqualTo("COMPLETO");
        assertThat(blocosBanco).isEqualTo(3);
        assertThat(contagemPontos).isEqualTo(3);
    }

    /**
     * Tarefa 8.16: Falha em um bloco nao afeta os demais, lote permanece ABERTO.
     * Processa bloco 1 com sucesso e bloco 2 com valor que estoura DECIMAL(28,12).
     * Comprova reversao total da transacao do bloco 2 (ponto nao gravado, contador nao incrementado)
     * e que o bloco 1 permanece integro com lote em estado ABERTO.
     */
    @Test
    void falhaEmUmBlocoNaoAfetaBlocoAnteriorELotePermaneceAberto() {
        String loteExternoId = "it-multibloco-" + UUID.randomUUID();
        LocalDate dataRef = LocalDate.of(2026, 8, 21);

        PontoDadoMercado pontoValido = new PontoDadoMercado(
                "B3", "BVBG.086", dataRef, "MULTIBLOCO_FALHA_OK",
                new BigDecimal("10.500"), "TAXA_AJUSTE", null);

        // Bloco 1: Sucesso
        ingestaoService.processarBloco(
                "B3", "BVBG.086", dataRef, loteExternoId,
                UUID.randomUUID(),
                "evt-1", "sha256:" + "1".repeat(64), 1, 3,
                TipoPayload.INDIVIDUAL_QUOTES, List.of(pontoValido));

        // Bloco 2: Valor com 18 digitos antes do ponto decimal -> estoura DECIMAL(28,12) no banco
        PontoDadoMercado pontoInvalido = new PontoDadoMercado(
                "B3", "BVBG.086", dataRef, "MULTIBLOCO_FALHA_ERRO",
                new BigDecimal("999999999999999999"), "TAXA_AJUSTE", null);

        assertThatThrownBy(() -> ingestaoService.processarBloco(
                "B3", "BVBG.086", dataRef, loteExternoId,
                UUID.randomUUID(),
                "evt-2", "sha256:" + "2".repeat(64), 2, 3,
                TipoPayload.INDIVIDUAL_QUOTES, List.of(pontoInvalido)))
                .isInstanceOf(Exception.class);

        // Verificacoes diretas no banco de dados via SQL
        String estadoBanco = jdbcTemplate.queryForObject(
                "SELECT estado FROM lote_ingestao WHERE lote_externo_id = ?",
                String.class, loteExternoId);
        Integer blocosBanco = jdbcTemplate.queryForObject(
                "SELECT blocos_recebidos FROM lote_ingestao WHERE lote_externo_id = ?",
                Integer.class, loteExternoId);
        Integer pontosGravadosBanco = jdbcTemplate.queryForObject(
                "SELECT pontos_gravados FROM lote_ingestao WHERE lote_externo_id = ?",
                Integer.class, loteExternoId);

        assertThat(estadoBanco).isEqualTo("ABERTO");
        assertThat(blocosBanco).isEqualTo(1);
        assertThat(pontosGravadosBanco).isEqualTo(1);

        // Ponto do bloco 1 continua persistido
        Integer contagemPontoValido = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ponto_dado_mercado WHERE chave_instrumento = 'MULTIBLOCO_FALHA_OK'",
                Integer.class);
        assertThat(contagemPontoValido).isEqualTo(1);

        // Ponto do bloco 2 que falhou NAO foi gravado (transacao revertida por completo)
        Integer contagemPontoInvalido = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ponto_dado_mercado WHERE chave_instrumento = 'MULTIBLOCO_FALHA_ERRO'",
                Integer.class);
        assertThat(contagemPontoInvalido).isEqualTo(0);
    }

    /**
     * Tarefa 8.17: Lote incompleto nunca fica com estado indicando publicacao.
     * Envia apenas 2 de 3 blocos. Comprova que o lote permanece ABERTO (nunca COMPLETO),
     * impedindo emissao indevida de eventos de dado normalizado ou calculo de curva.
     */
    @Test
    void loteIncompletoPermaneceAbertoENaoConsolida() {
        String loteExternoId = "it-multibloco-" + UUID.randomUUID();
        LocalDate dataRef = LocalDate.of(2026, 8, 21);

        PontoDadoMercado ponto1 = new PontoDadoMercado(
                "B3", "BVBG.086", dataRef, "MULTIBLOCO_INC_1",
                new BigDecimal("10.000"), "TAXA_AJUSTE", null);
        PontoDadoMercado ponto2 = new PontoDadoMercado(
                "B3", "BVBG.086", dataRef, "MULTIBLOCO_INC_2",
                new BigDecimal("11.000"), "TAXA_AJUSTE", null);

        // Bloco 1 de 3
        ingestaoService.processarBloco(
                "B3", "BVBG.086", dataRef, loteExternoId,
                UUID.randomUUID(),
                "evt-1", "sha256:" + "1".repeat(64), 1, 3,
                TipoPayload.INDIVIDUAL_QUOTES, List.of(ponto1));

        // Bloco 2 de 3 (bloco 3 nao e enviado)
        ingestaoService.processarBloco(
                "B3", "BVBG.086", dataRef, loteExternoId,
                UUID.randomUUID(),
                "evt-2", "sha256:" + "2".repeat(64), 2, 3,
                TipoPayload.INDIVIDUAL_QUOTES, List.of(ponto2));

        // Verificacoes diretas no banco de dados via SQL
        String estadoBanco = jdbcTemplate.queryForObject(
                "SELECT estado FROM lote_ingestao WHERE lote_externo_id = ?",
                String.class, loteExternoId);
        Integer blocosBanco = jdbcTemplate.queryForObject(
                "SELECT blocos_recebidos FROM lote_ingestao WHERE lote_externo_id = ?",
                Integer.class, loteExternoId);
        Integer pontosGravadosBanco = jdbcTemplate.queryForObject(
                "SELECT pontos_gravados FROM lote_ingestao WHERE lote_externo_id = ?",
                Integer.class, loteExternoId);
        Integer contagemPontos = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ponto_dado_mercado WHERE chave_instrumento IN ('MULTIBLOCO_INC_1', 'MULTIBLOCO_INC_2')",
                Integer.class);

        assertThat(estadoBanco).isEqualTo("ABERTO");
        assertThat(blocosBanco).isEqualTo(2);
        assertThat(pontosGravadosBanco).isEqualTo(2);
        assertThat(contagemPontos).isEqualTo(2);
    }

    /**
     * Tarefa 8.1: Idempotencia com comparacao de estado completo do banco.
     * Processa um bloco e captura todos os atributos do lote e ponto gravado.
     * Em seguida, reprocessa exatamente o mesmo bloco (mesmo loteExternoId, eventId e pontos)
     * e comprova que nenhum contador, valor ou registro sofre alteracao ou duplicacao.
     */
    @Test
    void redeliveryIdempotenteNaoAlteraEstadoCompletoDoBanco() {
        String loteExternoId = "it-multibloco-" + UUID.randomUUID();
        String eventId = "evt-1";
        LocalDate dataRef = LocalDate.of(2026, 8, 21);
        BigDecimal valorPonto = new BigDecimal("15.250");

        PontoDadoMercado ponto = new PontoDadoMercado(
                "B3", "BVBG.086", dataRef, "MULTIBLOCO_IDEM",
                valorPonto, "TAXA_AJUSTE", null);

        // 1a chamada: processamento inicial do bloco
        ResultadoProcessamentoBloco r1 = ingestaoService.processarBloco(
                "B3", "BVBG.086", dataRef, loteExternoId,
                UUID.randomUUID(), eventId, "sha256:" + "a".repeat(64), 1, 1,
                TipoPayload.INDIVIDUAL_QUOTES, List.of(ponto));

        assertThat(r1.lote().estado()).isEqualTo(EstadoLoteIngestao.COMPLETO);

        // Captura o estado completo no banco apos o primeiro processamento
        String estadoAntes = jdbcTemplate.queryForObject(
                "SELECT estado FROM lote_ingestao WHERE lote_externo_id = ?",
                String.class, loteExternoId);
        Integer blocosAntes = jdbcTemplate.queryForObject(
                "SELECT blocos_recebidos FROM lote_ingestao WHERE lote_externo_id = ?",
                Integer.class, loteExternoId);
        Integer pontosGravadosAntes = jdbcTemplate.queryForObject(
                "SELECT pontos_gravados FROM lote_ingestao WHERE lote_externo_id = ?",
                Integer.class, loteExternoId);
        Integer pontosRecebidosAntes = jdbcTemplate.queryForObject(
                "SELECT pontos_recebidos FROM lote_ingestao WHERE lote_externo_id = ?",
                Integer.class, loteExternoId);
        BigDecimal valorAntes = jdbcTemplate.queryForObject(
                "SELECT valor FROM ponto_dado_mercado WHERE chave_instrumento = 'MULTIBLOCO_IDEM'",
                BigDecimal.class);
        Integer contagemPontosAntes = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ponto_dado_mercado WHERE chave_instrumento = 'MULTIBLOCO_IDEM'",
                Integer.class);
        Integer contagemLotesAntes = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM lote_ingestao WHERE lote_externo_id = ?",
                Integer.class, loteExternoId);

        assertThat(estadoAntes).isEqualTo("COMPLETO");
        assertThat(blocosAntes).isEqualTo(1);
        assertThat(pontosGravadosAntes).isEqualTo(1);
        assertThat(pontosRecebidosAntes).isEqualTo(1);
        assertThat(valorAntes).isEqualByComparingTo(valorPonto);
        assertThat(contagemPontosAntes).isEqualTo(1);
        assertThat(contagemLotesAntes).isEqualTo(1);

        // 2a chamada: redelivery do MESMO bloco (mesmos argumentos e eventId)
        ResultadoProcessamentoBloco r2 = ingestaoService.processarBloco(
                "B3", "BVBG.086", dataRef, loteExternoId,
                UUID.randomUUID(), eventId, "sha256:" + "a".repeat(64), 1, 1,
                TipoPayload.INDIVIDUAL_QUOTES, List.of(ponto));

        assertThat(r2.divergenciasNoBloco()).isEmpty();

        // Captura o estado no banco apos o redelivery
        String estadoDepois = jdbcTemplate.queryForObject(
                "SELECT estado FROM lote_ingestao WHERE lote_externo_id = ?",
                String.class, loteExternoId);
        Integer blocosDepois = jdbcTemplate.queryForObject(
                "SELECT blocos_recebidos FROM lote_ingestao WHERE lote_externo_id = ?",
                Integer.class, loteExternoId);
        Integer pontosGravadosDepois = jdbcTemplate.queryForObject(
                "SELECT pontos_gravados FROM lote_ingestao WHERE lote_externo_id = ?",
                Integer.class, loteExternoId);
        Integer pontosRecebidosDepois = jdbcTemplate.queryForObject(
                "SELECT pontos_recebidos FROM lote_ingestao WHERE lote_externo_id = ?",
                Integer.class, loteExternoId);
        BigDecimal valorDepois = jdbcTemplate.queryForObject(
                "SELECT valor FROM ponto_dado_mercado WHERE chave_instrumento = 'MULTIBLOCO_IDEM'",
                BigDecimal.class);
        Integer contagemPontosDepois = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ponto_dado_mercado WHERE chave_instrumento = 'MULTIBLOCO_IDEM'",
                Integer.class);
        Integer contagemLotesDepois = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM lote_ingestao WHERE lote_externo_id = ?",
                Integer.class, loteExternoId);

        // Comprova que absolutamente nada mudou no banco
        assertThat(estadoDepois).isEqualTo(estadoAntes);
        assertThat(blocosDepois).isEqualTo(blocosAntes);
        assertThat(pontosGravadosDepois).isEqualTo(pontosGravadosAntes);
        assertThat(pontosRecebidosDepois).isEqualTo(pontosRecebidosAntes);
        assertThat(valorDepois).isEqualByComparingTo(valorAntes);
        assertThat(contagemPontosDepois).isEqualTo(contagemPontosAntes);
        assertThat(contagemLotesDepois).isEqualTo(contagemLotesAntes);
    }
}
