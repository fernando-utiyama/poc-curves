package com.poccurves.engine.dto;

import com.poccurves.engine.domain.curva.Vertice;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Formas de request/response HTTP do curve-engine — mesmo padrão real já usado por
 * curve-api ({@code dto.ApiDtos}) e curve-bff ({@code dto.BffDtos}): um único arquivo,
 * registros aninhados, pacote {@code dto} separado de {@code domain} (que é regra de
 * negócio pura, não forma de adaptador HTTP).
 */
public class EngineDtos {

    public record ComparacaoModelosRequest(
            String codigoCurva,
            LocalDate dataReferencia,
            String momento,
            String modeloA,
            String modeloB
    ) {}

    public record ItemComparacaoDTO(
            int prazoDiasUteis,
            BigDecimal taxaA,
            BigDecimal taxaB,
            BigDecimal diferencaTaxaBps,
            BigDecimal fatorDescontoA,
            BigDecimal fatorDescontoB,
            String status // COINCIDENTE, PRESENTE_APENAS_EM_A, PRESENTE_APENAS_EM_B
    ) {}

    public record ComparacaoResponse(
            LocalDate dataReferencia,
            String rotuloCurvaA,
            String rotuloCurvaB,
            List<ItemComparacaoDTO> diferencas
    ) {}

    public record ImportarModeloGroovyRequest(
            String codigo,
            String nome,
            String scriptGroovy
    ) {}

    public record ImportarModeloResponse(
            UUID id,
            String codigo,
            String status, // VALIDO, ERRO_COMPILACAO, ERRO_EXECUCAO_TESTE
            String checksum,
            String mensagem
    ) {}

    public record InterpolacaoRequest(
            LocalDate dataReferencia,
            String momento,
            Integer versao,
            List<Integer> prazosDiasUteis
    ) {}

    public record ItemInterpolacaoResultado(
            int prazoDiasUteis,
            String taxa,
            String fatorDesconto,
            String status,
            String erroMensagem
    ) {}

    public record InterpolacaoResponse(
            String codigoCurva,
            int versaoUtilizada,
            String interpoladorUtilizado,
            List<ItemInterpolacaoResultado> resultados
    ) {}

    public record ModeloDTO(
            UUID id,
            String codigo,
            String nome,
            String tipo, // BUILTIN, GROOVY
            String estado, // ATIVO, DESABILITADO
            String checksum,
            Instant criadoEm
    ) {}

    public record ModelosResponse(List<ModeloDTO> modelos) {}

    public record TestarScriptGroovyRequest(
            String scriptGroovy,
            List<InsumoAmostraDTO> insumos
    ) {
        public record InsumoAmostraDTO(String ticker, BigDecimal taxaAjuste, Integer diasUteisVencimento, LocalDate dataVencimento) {}
    }

    public record TestarScriptGroovyResponse(
            String status, // OK, ERRO_COMPILACAO, ERRO_EXECUCAO
            String mensagem,
            List<Vertice> vertices
    ) {}

    public record ConstrucaoCurvaRequest(
            String curveCode,
            LocalDate referenceDate,
            String curveMoment,
            UUID runId,
            UUID executionId
    ) {}
}

