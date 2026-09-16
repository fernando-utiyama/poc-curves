package com.poccurves.processor.application.usecase;
import com.poccurves.processor.application.exception.DatasetDesconhecidoException;
import com.poccurves.processor.application.exception.OraculoTaxaSwapDivergenteException;
import com.poccurves.processor.application.exception.OraculoTaxaSwapIndisponivelException;
import com.poccurves.processor.application.exception.ParseFalhouException;
import com.poccurves.processor.application.model.B3TaxaSwapParser;
import com.poccurves.processor.application.model.DatasetParser;
import com.poccurves.processor.application.model.DatasetParserRegistry;
import com.poccurves.processor.application.model.DefinicaoCurvaResumo;
import com.poccurves.processor.application.model.EstadoLoteIngestao;
import com.poccurves.processor.application.model.LoteIngestao;
import com.poccurves.processor.application.model.ModoOrigem;
import com.poccurves.processor.application.model.MomentoCurva;
import com.poccurves.processor.application.model.OrigemVersao;
import com.poccurves.processor.application.model.ParseResult;
import com.poccurves.processor.application.model.ResultadoProcessamentoBloco;
import com.poccurves.processor.application.model.TipoPayload;
import com.poccurves.processor.application.model.VersaoCurva;
import com.poccurves.processor.application.model.VerticeCurva;
import com.poccurves.processor.application.port.BlobStorageReadPort;
import com.poccurves.processor.application.port.DefinicaoCurvaLeituraRepositoryPort;
import com.poccurves.processor.application.port.ExecucaoCurvaLeituraRepositoryPort;
import com.poccurves.processor.application.port.NormalizedEventPort;
import com.poccurves.processor.application.port.PontoDadoMercadoRepositoryPort;
import com.poccurves.processor.application.port.VersaoCurvaRepositoryPort;
import com.poccurves.processor.application.port.VerticeCurvaRepositoryPort;
import com.poccurves.processor.application.util.MetricasIngestao;

import com.poccurves.common.event.EventEnvelope;
import com.poccurves.common.event.EventSource;
import com.poccurves.common.event.PayloadKind;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Testa o roteamento por dataset e o parsing — extraídos de {@code IngestaoListener} para cá
 * (application) na migração para arquitetura hexagonal. Cobre a tarefa 8.6 do backlog
 * (dead-letter para dataset desconhecido e payload não parseável) no nível de unidade.
 */
class ProcessarEnvelopeIngestaoUseCaseTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final byte[] CONTEUDO_BLOB_TESTE = "<x/>".getBytes(StandardCharsets.UTF_8);

    private static String sha256Hex(byte[] conteudo) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(conteudo);
        StringBuilder hex = new StringBuilder(hash.length * 2);
        for (byte b : hash) {
            hex.append(String.format("%02x", b));
        }
        return hex.toString();
    }

    private BlobStorageReadPort blobStorageReadPortFake() {
        BlobStorageReadPort mockPort = mock(BlobStorageReadPort.class);
        when(mockPort.existe("b3", "2026-08-21/teste.xml")).thenReturn(true);
        when(mockPort.baixar("b3", "2026-08-21/teste.xml")).thenReturn(CONTEUDO_BLOB_TESTE);
        return mockPort;
    }

    private EventEnvelope envelopeValido(String dataset) throws Exception {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("sourceUrl", "file://teste");
        payload.put("encoding", "UTF-8");
        payload.put("contentHash", "sha256:" + sha256Hex(CONTEUDO_BLOB_TESTE));
        payload.put("sizeBytes", CONTEUDO_BLOB_TESTE.length);
        payload.put("blobContainer", "b3");
        payload.put("blobPath", "2026-08-21/teste.xml");

        return new EventEnvelope(
                UUID.fromString("a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11"),
                UUID.fromString("b0eebc99-9c0b-4ef8-bb6d-6bb9bd380a22"),
                EventSource.B3,
                dataset,
                LocalDate.of(2026, 8, 21),
                Instant.parse("2026-08-21T18:00:00Z"),
                "1.0",
                PayloadKind.INDIVIDUAL_QUOTES,
                "lote-1",
                1,
                1,
                payload,
                null,
                null
        );
    }

    @Test
    void datasetDesconhecidoLancaExcecaoNomeadaSemChamarIngestaoService() throws Exception {
        DatasetParserRegistry registryVazio = new DatasetParserRegistry(List.of());
        IngestaoService ingestaoServiceMock = mock(IngestaoService.class);
        ProcessarEnvelopeIngestaoUseCase useCase = new ProcessarEnvelopeIngestaoUseCase(
                registryVazio, ingestaoServiceMock, new MetricasIngestao(new SimpleMeterRegistry()),
                mock(ExecucaoCurvaLeituraRepositoryPort.class), mock(PontoDadoMercadoRepositoryPort.class),
                mock(PublicacaoCurvaService.class), mock(NormalizedEventPort.class), blobStorageReadPortFake(),
                mock(DefinicaoCurvaLeituraRepositoryPort.class), mock(VersaoCurvaRepositoryPort.class), mock(VerticeCurvaRepositoryPort.class));

        EventEnvelope envelope = envelopeValido("DATASET_QUE_NAO_EXISTE");

        assertThatThrownBy(() -> useCase.processar(envelope))
                .isInstanceOf(DatasetDesconhecidoException.class)
                .hasMessageContaining("DATASET_QUE_NAO_EXISTE");

        verify(ingestaoServiceMock, never()).processarBloco(any(), any(), any(), any(), any(), any(), any(), anyInt(), anyInt(), any(), any());
    }

    @Test
    void payloadNaoParseavelLancaExcecaoNomeadaSemChamarIngestaoService() throws Exception {
        DatasetParser parserQueFalha = new DatasetParser() {
            @Override
            public String dataset() {
                return "DATASET_COM_PARSE_QUEBRADO";
            }

            @Override
            public ParseResult parse(byte[] conteudo, String encoding, LocalDate referenceDate) {
                return new ParseResult.Falha("conteúdo malformado de propósito", "diagnóstico de teste");
            }
        };
        DatasetParserRegistry registry = new DatasetParserRegistry(List.of(parserQueFalha));
        IngestaoService ingestaoServiceMock = mock(IngestaoService.class);
        ProcessarEnvelopeIngestaoUseCase useCase = new ProcessarEnvelopeIngestaoUseCase(
                registry, ingestaoServiceMock, new MetricasIngestao(new SimpleMeterRegistry()),
                mock(ExecucaoCurvaLeituraRepositoryPort.class), mock(PontoDadoMercadoRepositoryPort.class),
                mock(PublicacaoCurvaService.class), mock(NormalizedEventPort.class), blobStorageReadPortFake(),
                mock(DefinicaoCurvaLeituraRepositoryPort.class), mock(VersaoCurvaRepositoryPort.class), mock(VerticeCurvaRepositoryPort.class));

        EventEnvelope envelope = envelopeValido("DATASET_COM_PARSE_QUEBRADO");

        assertThatThrownBy(() -> useCase.processar(envelope))
                .isInstanceOf(ParseFalhouException.class)
                .hasMessageContaining("conteúdo malformado de propósito");

        verify(ingestaoServiceMock, never()).processarBloco(any(), any(), any(), any(), any(), any(), any(), anyInt(), anyInt(), any(), any());
    }

    private static int anyInt() {
        return org.mockito.ArgumentMatchers.anyInt();
    }

    // ---- Oráculo cruzado de PRE para curvas do TaxaSwap.txt (openspec/changes/b3-additional-curves) ----

    /** Linhas reais de docs/TaxaSwap.txt (geração 2026-09-14), mesmas usadas em B3TaxaSwapParserTest. */
    private static final String LINHA_PRE_REAL = "0146360010120260914T1PRE  DIxPRE         0000100001+00000139000000F00001";
    private static final String LINHA_DCL_REAL = "0049060010120260914T1DCL  CUPOM LIMPO - S0000100001-00001179600000F00001";
    private static final byte[] CONTEUDO_TAXA_SWAP = String.join("\n", LINHA_PRE_REAL, LINHA_DCL_REAL)
            .getBytes(StandardCharsets.ISO_8859_1);
    private static final UUID CORRELATION_ID_ORACULO = UUID.fromString("c0eebc99-9c0b-4ef8-bb6d-6bb9bd380a33");
    private static final UUID EXECUCAO_ID_ORACULO = UUID.fromString("d0eebc99-9c0b-4ef8-bb6d-6bb9bd380a44");
    private static final UUID DEFINICAO_PRE_ORACULO_ID = UUID.fromString("e0eebc99-9c0b-4ef8-bb6d-6bb9bd380a55");
    private static final UUID VERSAO_DEF_PRE_ORACULO_ID = UUID.fromString("f0eebc99-9c0b-4ef8-bb6d-6bb9bd380a66");
    private static final UUID VERSAO_CURVA_PRE_ORACULO_ID = UUID.fromString("a1eebc99-9c0b-4ef8-bb6d-6bb9bd380a77");

    private DatasetParserRegistry registryTaxaSwapDcl() {
        return new DatasetParserRegistry(List.of(
                new B3TaxaSwapParser("B3_TAXA_SWAP_DCL", "DCL"),
                new B3TaxaSwapParser("B3_TAXA_SWAP_PRE", "PRE")));
    }

    private BlobStorageReadPort blobStorageReadPortComTaxaSwap() {
        BlobStorageReadPort mockPort = mock(BlobStorageReadPort.class);
        when(mockPort.existe("b3", "2026-09-14/TaxaSwap.txt")).thenReturn(true);
        when(mockPort.baixar("b3", "2026-09-14/TaxaSwap.txt")).thenReturn(CONTEUDO_TAXA_SWAP);
        return mockPort;
    }

    private EventEnvelope envelopeTaxaSwap(String dataset) throws Exception {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("sourceUrl", "file://teste-taxa-swap");
        payload.put("encoding", "ISO-8859-1");
        payload.put("contentHash", "sha256:" + sha256Hex(CONTEUDO_TAXA_SWAP));
        payload.put("sizeBytes", CONTEUDO_TAXA_SWAP.length);
        payload.put("blobContainer", "b3");
        payload.put("blobPath", "2026-09-14/TaxaSwap.txt");

        return new EventEnvelope(
                UUID.fromString("a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11"),
                CORRELATION_ID_ORACULO,
                EventSource.B3,
                dataset,
                LocalDate.of(2026, 9, 14),
                Instant.parse("2026-09-14T18:00:00Z"),
                "1.0",
                PayloadKind.READY_CURVE,
                "lote-taxa-swap",
                1,
                1,
                payload,
                null,
                null
        );
    }

    private IngestaoService ingestaoServiceMockRetornandoLoteCompleto() {
        IngestaoService ingestaoServiceMock = mock(IngestaoService.class);
        LoteIngestao loteCompleto = LoteIngestao.reidratar(
                1L, EXECUCAO_ID_ORACULO, "B3", "B3_TAXA_SWAP_DCL", TipoPayload.READY_CURVE,
                LocalDate.of(2026, 9, 14), "lote-taxa-swap", CORRELATION_ID_ORACULO, "evt-1",
                "hash-1", 1, 1, 1, 1, 0, null, EstadoLoteIngestao.COMPLETO, Instant.now());
        when(ingestaoServiceMock.processarBloco(any(), any(), any(), any(), any(), any(), any(), anyInt(), anyInt(), any(), any()))
                .thenReturn(new ResultadoProcessamentoBloco(loteCompleto, List.of()));
        return ingestaoServiceMock;
    }

    private ExecucaoCurvaLeituraRepositoryPort execucaoCurvaLeituraRepositoryPortComMomentoDefinido() {
        ExecucaoCurvaLeituraRepositoryPort mockPort = mock(ExecucaoCurvaLeituraRepositoryPort.class);
        when(mockPort.buscarPorCorrelacaoId(CORRELATION_ID_ORACULO)).thenReturn(
                Optional.of(new ExecucaoCurvaLeituraRepositoryPort.ExecucaoCurvaResumo(EXECUCAO_ID_ORACULO, MomentoCurva.FECHAMENTO)));
        return mockPort;
    }

    @Test
    void oraculoIndisponivelBloqueiaPublicacaoDeCurvaTaxaSwap() throws Exception {
        DefinicaoCurvaLeituraRepositoryPort definicaoCurvaLeituraRepositoryMock = mock(DefinicaoCurvaLeituraRepositoryPort.class);
        when(definicaoCurvaLeituraRepositoryMock.resolverPorCodigo("B3_CURVA_PRE")).thenReturn(Optional.empty());

        PublicacaoCurvaService publicacaoCurvaServiceMock = mock(PublicacaoCurvaService.class);
        ProcessarEnvelopeIngestaoUseCase useCase = new ProcessarEnvelopeIngestaoUseCase(
                registryTaxaSwapDcl(), ingestaoServiceMockRetornandoLoteCompleto(), new MetricasIngestao(new SimpleMeterRegistry()),
                execucaoCurvaLeituraRepositoryPortComMomentoDefinido(), mock(PontoDadoMercadoRepositoryPort.class),
                publicacaoCurvaServiceMock, mock(NormalizedEventPort.class), blobStorageReadPortComTaxaSwap(),
                definicaoCurvaLeituraRepositoryMock, mock(VersaoCurvaRepositoryPort.class), mock(VerticeCurvaRepositoryPort.class));

        assertThatThrownBy(() -> useCase.processar(envelopeTaxaSwap("B3_TAXA_SWAP_DCL")))
                .isInstanceOf(OraculoTaxaSwapIndisponivelException.class);

        verify(publicacaoCurvaServiceMock, never()).publicarCurvaImportada(any(), any(), any(), any(), anyLong(), any(), any(), any());
    }

    @Test
    void oraculoDivergenteBloqueiaPublicacaoDeCurvaTaxaSwap() throws Exception {
        DefinicaoCurvaLeituraRepositoryPort definicaoCurvaLeituraRepositoryMock = mock(DefinicaoCurvaLeituraRepositoryPort.class);
        when(definicaoCurvaLeituraRepositoryMock.resolverPorCodigo("B3_CURVA_PRE")).thenReturn(Optional.of(
                new DefinicaoCurvaResumo(DEFINICAO_PRE_ORACULO_ID, "B3_CURVA_PRE", ModoOrigem.IMPORTED, VERSAO_DEF_PRE_ORACULO_ID, 1)));

        VersaoCurvaRepositoryPort versaoCurvaRepositoryMock = mock(VersaoCurvaRepositoryPort.class);
        VersaoCurva versaoPreOraculo = VersaoCurva.criar(
                DEFINICAO_PRE_ORACULO_ID, VERSAO_DEF_PRE_ORACULO_ID, LocalDate.of(2026, 9, 14),
                MomentoCurva.FECHAMENTO, 1, OrigemVersao.IMPORTADA, EXECUCAO_ID_ORACULO);
        when(versaoCurvaRepositoryMock.buscarPublicadaAtual(DEFINICAO_PRE_ORACULO_ID, LocalDate.of(2026, 9, 14), MomentoCurva.FECHAMENTO))
                .thenReturn(Optional.of(versaoPreOraculo));

        VerticeCurvaRepositoryPort verticeCurvaRepositoryMock = mock(VerticeCurvaRepositoryPort.class);
        // Oráculo publica 14.000 para o prazo de 1 dia útil — o TaxaSwap.txt real diz 13.900 (LINHA_PRE_REAL).
        when(verticeCurvaRepositoryMock.buscarPorVersaoCurvaId(versaoPreOraculo.id())).thenReturn(
                List.of(new VerticeCurva(1, null, null, new BigDecimal("14.000"), null)));

        PublicacaoCurvaService publicacaoCurvaServiceMock = mock(PublicacaoCurvaService.class);
        ProcessarEnvelopeIngestaoUseCase useCase = new ProcessarEnvelopeIngestaoUseCase(
                registryTaxaSwapDcl(), ingestaoServiceMockRetornandoLoteCompleto(), new MetricasIngestao(new SimpleMeterRegistry()),
                execucaoCurvaLeituraRepositoryPortComMomentoDefinido(), mock(PontoDadoMercadoRepositoryPort.class),
                publicacaoCurvaServiceMock, mock(NormalizedEventPort.class), blobStorageReadPortComTaxaSwap(),
                definicaoCurvaLeituraRepositoryMock, versaoCurvaRepositoryMock, verticeCurvaRepositoryMock);

        assertThatThrownBy(() -> useCase.processar(envelopeTaxaSwap("B3_TAXA_SWAP_DCL")))
                .isInstanceOf(OraculoTaxaSwapDivergenteException.class)
                .hasMessageContaining("[1]");

        verify(publicacaoCurvaServiceMock, never()).publicarCurvaImportada(any(), any(), any(), any(), anyLong(), any(), any(), any());
    }

    @Test
    void oraculoConsistentePermitePublicacaoDeCurvaTaxaSwap() throws Exception {
        DefinicaoCurvaLeituraRepositoryPort definicaoCurvaLeituraRepositoryMock = mock(DefinicaoCurvaLeituraRepositoryPort.class);
        when(definicaoCurvaLeituraRepositoryMock.resolverPorCodigo("B3_CURVA_PRE")).thenReturn(Optional.of(
                new DefinicaoCurvaResumo(DEFINICAO_PRE_ORACULO_ID, "B3_CURVA_PRE", ModoOrigem.IMPORTED, VERSAO_DEF_PRE_ORACULO_ID, 1)));

        VersaoCurvaRepositoryPort versaoCurvaRepositoryMock = mock(VersaoCurvaRepositoryPort.class);
        VersaoCurva versaoPreOraculo = VersaoCurva.criar(
                DEFINICAO_PRE_ORACULO_ID, VERSAO_DEF_PRE_ORACULO_ID, LocalDate.of(2026, 9, 14),
                MomentoCurva.FECHAMENTO, 1, OrigemVersao.IMPORTADA, EXECUCAO_ID_ORACULO);
        when(versaoCurvaRepositoryMock.buscarPublicadaAtual(DEFINICAO_PRE_ORACULO_ID, LocalDate.of(2026, 9, 14), MomentoCurva.FECHAMENTO))
                .thenReturn(Optional.of(versaoPreOraculo));

        VerticeCurvaRepositoryPort verticeCurvaRepositoryMock = mock(VerticeCurvaRepositoryPort.class);
        // Oráculo publica exatamente 13.900 para o prazo de 1 dia útil, igual ao TaxaSwap.txt real (LINHA_PRE_REAL).
        when(verticeCurvaRepositoryMock.buscarPorVersaoCurvaId(versaoPreOraculo.id())).thenReturn(
                List.of(new VerticeCurva(1, null, null, new BigDecimal("13.900"), null)));

        PublicacaoCurvaService publicacaoCurvaServiceMock = mock(PublicacaoCurvaService.class);
        when(publicacaoCurvaServiceMock.publicarCurvaImportada(any(), any(), any(), any(), anyLong(), any(), any(), any()))
                .thenReturn(VersaoCurva.criar(
                        UUID.randomUUID(), UUID.randomUUID(), LocalDate.of(2026, 9, 14),
                        MomentoCurva.FECHAMENTO, 1, OrigemVersao.IMPORTADA, EXECUCAO_ID_ORACULO));

        ProcessarEnvelopeIngestaoUseCase useCase = new ProcessarEnvelopeIngestaoUseCase(
                registryTaxaSwapDcl(), ingestaoServiceMockRetornandoLoteCompleto(), new MetricasIngestao(new SimpleMeterRegistry()),
                execucaoCurvaLeituraRepositoryPortComMomentoDefinido(), mock(PontoDadoMercadoRepositoryPort.class),
                publicacaoCurvaServiceMock, mock(NormalizedEventPort.class), blobStorageReadPortComTaxaSwap(),
                definicaoCurvaLeituraRepositoryMock, versaoCurvaRepositoryMock, verticeCurvaRepositoryMock);

        useCase.processar(envelopeTaxaSwap("B3_TAXA_SWAP_DCL"));

        verify(publicacaoCurvaServiceMock).publicarCurvaImportada(any(), any(), any(), any(), anyLong(), any(), any(), any());
    }
}
