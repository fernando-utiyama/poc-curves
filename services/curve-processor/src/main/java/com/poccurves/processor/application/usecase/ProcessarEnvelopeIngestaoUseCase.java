package com.poccurves.processor.application.usecase;
import com.poccurves.processor.application.exception.BlobNaoEncontradoException;
import com.poccurves.processor.application.exception.DatasetDesconhecidoException;
import com.poccurves.processor.application.exception.EnvelopeInvalidoException;
import com.poccurves.processor.application.exception.IntegridadeBlobException;
import com.poccurves.processor.application.exception.ParseFalhouException;
import com.poccurves.processor.application.model.B3TaxaSwapParser;
import com.poccurves.processor.application.model.DatasetParser;
import com.poccurves.processor.application.model.DatasetParserRegistry;
import com.poccurves.processor.application.model.EstadoLoteIngestao;
import com.poccurves.processor.application.model.LoteIngestao;
import com.poccurves.processor.application.model.ParseResult;
import com.poccurves.processor.application.model.PontoDadoMercado;
import com.poccurves.processor.application.model.ResultadoProcessamentoBloco;
import com.poccurves.processor.application.model.TipoPayload;
import com.poccurves.processor.application.model.VerticeCurva;
import com.poccurves.processor.application.port.BlobStorageReadPort;
import com.poccurves.processor.application.port.BtrsCurvaPrimrRepositoryPort;
import com.poccurves.processor.application.port.ExecucaoCurvaLeituraRepositoryPort;
import com.poccurves.processor.application.port.NormalizedEventPort;
import com.poccurves.processor.application.port.PontoDadoMercadoRepositoryPort;
import com.poccurves.processor.application.util.MetricasIngestao;

import com.poccurves.common.event.EventEnvelope;
import com.poccurves.common.event.PayloadKind;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Optional;

/**
 * Orquestração de nível de envelope (roteamento por dataset, parsing, persistência do bloco,
 * publicação condicional de eventos derivados) — extraída do listener Kafka na migração para
 * arquitetura hexagonal (openspec/changes/hexagonal-architecture): o listener (adapter/in/messaging)
 * agora só desserializa a mensagem crua (String) em {@link EventEnvelope} e chama este caso de
 * uso; toda decisão de negócio mora aqui. `tools.jackson.databind.JsonNode` é permitido nesta
 * camada (ver ArchitectureTest do módulo) porque {@code EventEnvelope.payload()} já expõe esse
 * tipo como estrutura de dado genérica — não como uso de serviço de serialização (isso continua
 * banido: nenhum {@code ObjectMapper} aqui).
 */
public class ProcessarEnvelopeIngestaoUseCase {

    private static final Logger log = LoggerFactory.getLogger(ProcessarEnvelopeIngestaoUseCase.class);

    /**
     * Datasets do TaxaSwap.txt que gravam direto no schema legado (tBtrsCurvaPrimr,
     * db/migration/V22/V23/V24) em vez do caminho genérico (ponto_dado_mercado/lote_ingestao/
     * versao_curva/vertice_curva) — decisão do usuário: "substituir só para TS B3", primeira
     * curva adaptada ao schema legado real (openspec/changes/legado-schema-curvas-mercado).
     * PRE (DIxPRE) entra igual às demais — na primeira rodada (V23) tinha ficado de fora por
     * ter sido usado só pelo oráculo cruzado que este caminho substituiu, mas é uma curva do
     * arquivo como qualquer outra (achado corrigido na V24: faltava a linha em tCurvaMercd).
     * O antigo OraculoTaxaSwapValidator/OraculoTaxaSwap*Exception continuam no código, intactos
     * e testados, só que sem chamador agora — não apagados porque a mesma ideia pode voltar a
     * fazer sentido contra o schema novo.
     */
    private static final java.util.Set<String> DATASETS_TAXA_SWAP_SCHEMA_LEGADO = java.util.Set.of(
            "B3_TAXA_SWAP_DCL", "B3_TAXA_SWAP_PTX", "B3_TAXA_SWAP_INP", "B3_TAXA_SWAP_DPL", "B3_TAXA_SWAP_PRE");
    private static final String PREFIXO_DATASET_TAXA_SWAP = "B3_TAXA_SWAP_";

    private final DatasetParserRegistry parserRegistry;
    private final IngestaoService ingestaoService;
    private final MetricasIngestao metricasIngestao;
    private final ExecucaoCurvaLeituraRepositoryPort execucaoCurvaLeituraRepository;
    private final PontoDadoMercadoRepositoryPort pontoDadoMercadoRepository;
    private final PublicacaoCurvaService publicacaoCurvaService;
    private final NormalizedEventPort normalizedEventPort;
    private final BlobStorageReadPort blobStorageReadPort;
    private final BtrsCurvaPrimrRepositoryPort btrsCurvaPrimrRepository;

    public ProcessarEnvelopeIngestaoUseCase(
            DatasetParserRegistry parserRegistry,
            IngestaoService ingestaoService,
            MetricasIngestao metricasIngestao,
            ExecucaoCurvaLeituraRepositoryPort execucaoCurvaLeituraRepository,
            PontoDadoMercadoRepositoryPort pontoDadoMercadoRepository,
            PublicacaoCurvaService publicacaoCurvaService,
            NormalizedEventPort normalizedEventPort,
            BlobStorageReadPort blobStorageReadPort,
            BtrsCurvaPrimrRepositoryPort btrsCurvaPrimrRepository
    ) {
        this.parserRegistry = parserRegistry;
        this.ingestaoService = ingestaoService;
        this.metricasIngestao = metricasIngestao;
        this.execucaoCurvaLeituraRepository = execucaoCurvaLeituraRepository;
        this.pontoDadoMercadoRepository = pontoDadoMercadoRepository;
        this.publicacaoCurvaService = publicacaoCurvaService;
        this.normalizedEventPort = normalizedEventPort;
        this.blobStorageReadPort = blobStorageReadPort;
        this.btrsCurvaPrimrRepository = btrsCurvaPrimrRepository;
    }

    public void processar(EventEnvelope envelope) {
        metricasIngestao.eventosConsumidos().increment();
        log.info("bloco recebido para processamento");

        if (DATASETS_TAXA_SWAP_SCHEMA_LEGADO.contains(envelope.dataset())) {
            processarTaxaSwapSchemaLegado(envelope);
            return;
        }

        // Roteamento por dataset (não mais por payloadKind — tarefa 2.4 desatualizada:
        // desde que um parser real existe para o dataset de curva pronta B3_CURVA_PRE,
        // READY_CURVE segue o mesmo caminho genérico de resolução por dataset que
        // INDIVIDUAL_QUOTES; um dataset READY_CURVE sem parser registrado cai no mesmo
        // DatasetDesconhecidoException genérico de qualquer dataset desconhecido.
        DatasetParser parser = parserRegistry.resolver(envelope.dataset())
                .orElseThrow(() -> new DatasetDesconhecidoException(envelope.dataset()));

        JsonNode payload = envelope.payload();
        String encoding = textoObrigatorio(payload, "encoding");
        String contentHash = textoObrigatorio(payload, "contentHash");
        byte[] conteudo = buscarConteudoDoBlob(payload, contentHash);

        ParseResult parseResult = parser.parse(conteudo, encoding, envelope.referenceDate());
        if (parseResult instanceof ParseResult.Falha falha) {
            throw new ParseFalhouException(falha.motivo(), falha.diagnostico());
        }
        List<PontoDadoMercado> pontos = ((ParseResult.Sucesso) parseResult).pontos();

        TipoPayload tipoPayload = TipoPayload.valueOf(envelope.payloadKind().name());

        ResultadoProcessamentoBloco resultado = ingestaoService.processarBloco(
                envelope.source().name(),
                envelope.dataset(),
                envelope.referenceDate(),
                envelope.loteId(),
                envelope.correlationId(),
                envelope.eventId().toString(),
                contentHash,
                envelope.sequencia(),
                envelope.totalBlocos(),
                tipoPayload,
                pontos);

        metricasIngestao.pontosGravados().increment(pontos.size());
        metricasIngestao.divergencias().increment(resultado.divergenciasNoBloco().size());

        if (resultado.lote().estado() == EstadoLoteIngestao.COMPLETO) {
            normalizedEventPort.publicar(resultado.lote());
            if (envelope.payloadKind() == PayloadKind.READY_CURVE) {
                // DIAGNÓSTICO 12.2 (temporário): try/catch(Throwable) só para forçar log visível
                // -- o DefaultErrorHandler do Spring Kafka nesta versão não loga por padrão antes
                // de recuperar pra dead-letter (mesmo achado real já feito em curve-engine/
                // ConstrucaoRequestListener), então uma falha real aqui fica completamente muda.
                try {
                    publicarCurvaImportadaSePossivel(envelope, resultado.lote());
                } catch (Throwable t) {
                    log.error("DIAGNÓSTICO 12.2: falha ao publicar curva importada dataset={} referenceDate={}", envelope.dataset(), envelope.referenceDate(), t);
                    if (t instanceof RuntimeException re) {
                        throw re;
                    }
                    throw new RuntimeException(t);
                }
            }
        }
    }

    /**
     * Publica a curva pronta ingerida como uma nova versão IMPORTADA (tarefas 5.4-5.9,
     * agora conectadas ao caminho real de ingestão). Requer uma execucao_curva real
     * vinculada pelo correlationId do envelope — se não houver (aquisição disparada fora
     * do curve-orchestrator, ou execucao_curva sem momento_curva definido), os pontos
     * brutos já foram persistidos com sucesso em ponto_dado_mercado (isso não muda), só a
     * publicação da versão IMPORTADA fica de fora, com um aviso no log — não é um erro.
     * Exceções de negócio de {@link PublicacaoCurvaService#publicarCurvaImportada} (dataset
     * não mapeado, definição não IMPORTED, vértices vazios) propagam normalmente para a
     * dead-letter — esses SÃO problemas reais de configuração, ao contrário da ausência de
     * execucao_curva vinculada.
     */
    private void publicarCurvaImportadaSePossivel(EventEnvelope envelope, LoteIngestao lote) {
        Optional<ExecucaoCurvaLeituraRepositoryPort.ExecucaoCurvaResumo> execucaoOpt =
                execucaoCurvaLeituraRepository.buscarPorCorrelacaoId(envelope.correlationId());
        if (execucaoOpt.isEmpty()) {
            log.warn("nenhuma execucao_curva encontrada para correlationId={}; pontos de curva pronta persistidos, mas versão IMPORTADA não publicada", envelope.correlationId());
            return;
        }
        ExecucaoCurvaLeituraRepositoryPort.ExecucaoCurvaResumo execucao = execucaoOpt.get();
        if (execucao.momentoCurva() == null) {
            log.warn("execucao_curva {} não tem momento_curva definido; versão IMPORTADA não publicada", execucao.id());
            return;
        }

        List<PontoDadoMercado> pontos = pontoDadoMercadoRepository.buscarPorLoteIngestaoId(lote.id());
        List<VerticeCurva> vertices = pontos.stream()
                .map(p -> new VerticeCurva(Integer.parseInt(p.chaveInstrumento()), null, p.dataVencimento(), p.valor(), null))
                .toList();

        var versao = publicacaoCurvaService.publicarCurvaImportada(
                envelope.dataset(),
                envelope.referenceDate(),
                execucao.momentoCurva(),
                execucao.id(),
                lote.id(),
                envelope.dataset() + "/" + lote.loteExternoId(),
                lote.hashPayload(),
                vertices);

        log.info("curva importada publicada: dataset={} versionId={} versionNumber={} vertexCount={}",
                envelope.dataset(), versao.id(), versao.numeroVersao(), vertices.size());
    }

    /**
     * Caminho novo para as curvas TS B3 (DCL/PTX/INP/DPL, openspec/changes/legado-schema-curvas-
     * mercado) — grava direto em {@code tBtrsCurvaPrimr} (schema legado, db/migration/V22/V23),
     * substituindo por completo o caminho genérico (ponto_dado_mercado/lote_ingestao/
     * versao_curva/vertice_curva) para esses datasets. Não passa pelo {@link DatasetParserRegistry}
     * nem pelo {@link IngestaoService} — extrai os vértices direto do blob via
     * {@link B3TaxaSwapParser#extrairVertices} (que, diferente do caminho genérico, também
     * captura dias corridos, não só dias úteis — {@code tBtrsCurvaPrimr} tem coluna para os
     * dois) e grava via {@link BtrsCurvaPrimrRepositoryPort}, idempotente por delete-then-insert
     * (a tabela legada não tem chave natural — {@code cldtfdUnic} é um id arbitrário de
     * sequence — que permita upsert por outro meio).
     */
    private void processarTaxaSwapSchemaLegado(EventEnvelope envelope) {
        JsonNode payload = envelope.payload();
        String encoding = textoObrigatorio(payload, "encoding");
        String contentHash = textoObrigatorio(payload, "contentHash");
        byte[] conteudo = buscarConteudoDoBlob(payload, contentHash);

        String codigoCurva = envelope.dataset().substring(PREFIXO_DATASET_TAXA_SWAP.length());
        List<B3TaxaSwapParser.VerticeTaxaSwap> vertices;
        try {
            vertices = B3TaxaSwapParser.extrairVertices(conteudo, encoding, codigoCurva);
        } catch (IllegalArgumentException e) {
            throw new ParseFalhouException(e.getMessage(), "");
        }

        if (vertices.isEmpty()) {
            throw new ParseFalhouException(
                    "nenhum vértice encontrado para o código de curva " + codigoCurva + " no arquivo de taxas de swap", "");
        }

        btrsCurvaPrimrRepository.substituirVertices(envelope.dataset(), envelope.referenceDate(), vertices);

        metricasIngestao.pontosGravados().increment(vertices.size());
        log.info("vértices gravados em tBtrsCurvaPrimr: cTickerIndcd={} dBaseReft={} count={}",
                envelope.dataset(), envelope.referenceDate(), vertices.size());
    }

    /**
     * Busca o conteúdo bruto referenciado pelo evento em blob storage (blobContainer/blobPath,
     * contracts/events/marketdata-raw.schema.json) e confere o hash declarado antes de devolver
     * — openspec/changes/raw-file-blob-storage substitui o antigo caminho de reconstrução a
     * partir de payload.records[] por esta leitura de blob.
     */
    private byte[] buscarConteudoDoBlob(JsonNode payload, String contentHashEsperado) {
        String blobContainer = textoObrigatorio(payload, "blobContainer");
        String blobPath = textoObrigatorio(payload, "blobPath");

        if (!blobStorageReadPort.existe(blobContainer, blobPath)) {
            throw new BlobNaoEncontradoException(blobContainer, blobPath);
        }

        byte[] conteudo = blobStorageReadPort.baixar(blobContainer, blobPath);
        String hashCalculado = "sha256:" + calcularSha256Hex(conteudo);
        if (!hashCalculado.equals(contentHashEsperado)) {
            throw new IntegridadeBlobException(blobContainer, blobPath, contentHashEsperado, hashCalculado);
        }
        return conteudo;
    }

    private String calcularSha256Hex(byte[] conteudo) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(conteudo);
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 não disponível na JVM", e);
        }
    }

    private String textoObrigatorio(JsonNode payload, String campo) {
        JsonNode node = payload.get(campo);
        if (node == null || node.isNull()) {
            throw new EnvelopeInvalidoException("payload." + campo + " ausente");
        }
        return node.asText();
    }
}
