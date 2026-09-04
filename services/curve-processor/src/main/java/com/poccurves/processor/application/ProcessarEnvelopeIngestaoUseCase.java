package com.poccurves.processor.application;
import com.poccurves.processor.domain.curva.VerticeCurva;
import com.poccurves.processor.domain.ingestao.DatasetDesconhecidoException;
import com.poccurves.processor.domain.ingestao.EstadoLoteIngestao;
import com.poccurves.processor.domain.ingestao.LoteIngestao;
import com.poccurves.processor.domain.ingestao.PontoDadoMercado;
import com.poccurves.processor.domain.ingestao.ResultadoProcessamentoBloco;
import com.poccurves.processor.domain.parsing.DatasetParser;
import com.poccurves.processor.domain.parsing.DatasetParserRegistry;
import com.poccurves.processor.domain.parsing.EnvelopeInvalidoException;
import com.poccurves.processor.domain.parsing.ParseFalhouException;
import com.poccurves.processor.domain.parsing.ParseResult;
import com.poccurves.processor.domain.parsing.TipoPayload;

import com.poccurves.common.event.EventEnvelope;
import com.poccurves.common.event.PayloadKind;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;

import java.nio.charset.Charset;
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

    private final DatasetParserRegistry parserRegistry;
    private final IngestaoService ingestaoService;
    private final MetricasIngestao metricasIngestao;
    private final ExecucaoCurvaLeituraRepositoryPort execucaoCurvaLeituraRepository;
    private final PontoDadoMercadoRepositoryPort pontoDadoMercadoRepository;
    private final PublicacaoCurvaService publicacaoCurvaService;
    private final NormalizedEventPort normalizedEventPort;

    public ProcessarEnvelopeIngestaoUseCase(
            DatasetParserRegistry parserRegistry,
            IngestaoService ingestaoService,
            MetricasIngestao metricasIngestao,
            ExecucaoCurvaLeituraRepositoryPort execucaoCurvaLeituraRepository,
            PontoDadoMercadoRepositoryPort pontoDadoMercadoRepository,
            PublicacaoCurvaService publicacaoCurvaService,
            NormalizedEventPort normalizedEventPort
    ) {
        this.parserRegistry = parserRegistry;
        this.ingestaoService = ingestaoService;
        this.metricasIngestao = metricasIngestao;
        this.execucaoCurvaLeituraRepository = execucaoCurvaLeituraRepository;
        this.pontoDadoMercadoRepository = pontoDadoMercadoRepository;
        this.publicacaoCurvaService = publicacaoCurvaService;
        this.normalizedEventPort = normalizedEventPort;
    }

    public void processar(EventEnvelope envelope) {
        metricasIngestao.eventosConsumidos().increment();
        log.info("bloco recebido para processamento");

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
        byte[] conteudo = reconstruirConteudo(payload, encoding);

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
     * Reconstrói o conteúdo bruto do bloco a partir de {@code payload.records}
     * (contracts/events/marketdata-raw.schema.json): cada item é um objeto
     * com um campo {@code raw} contendo o fragmento estrutural original
     * (ex. um {@code <BizGrp>} inteiro, ou uma linha de um CSV), unidos por
     * `\n` na ordem em que aparecem. Um separador é necessário para conteúdo
     * baseado em linha (ex. a curva pronta da B3, um CSV) — sem ele, linhas
     * de registros diferentes ficariam coladas sem quebra. Não quebra o
     * conteúdo baseado em XML (ex. BVBG.086/BVBG.028): espaço em branco
     * entre elementos `<BizGrp>` irmãos é inócuo para o parser XML. Itens
     * sem o campo {@code raw} caem no fallback de serializar o próprio nó
     * de volta para texto, para não quebrar caso o conteúdo já chegue como
     * texto simples.
     */
    private byte[] reconstruirConteudo(JsonNode payload, String encoding) {
        JsonNode records = payload.get("records");
        StringBuilder builder = new StringBuilder();
        if (records != null && records.isArray()) {
            boolean primeiro = true;
            for (JsonNode item : records) {
                if (!primeiro) {
                    builder.append('\n');
                }
                primeiro = false;
                if (item.has("raw")) {
                    builder.append(item.get("raw").asText());
                } else if (item.isTextual()) {
                    builder.append(item.asText());
                } else {
                    builder.append(item.toString());
                }
            }
        }
        Charset charset;
        try {
            charset = Charset.forName(encoding);
        } catch (Exception e) {
            charset = java.nio.charset.StandardCharsets.UTF_8;
        }
        return builder.toString().getBytes(charset);
    }

    private String textoObrigatorio(JsonNode payload, String campo) {
        JsonNode node = payload.get(campo);
        if (node == null || node.isNull()) {
            throw new EnvelopeInvalidoException("payload." + campo + " ausente");
        }
        return node.asText();
    }
}
