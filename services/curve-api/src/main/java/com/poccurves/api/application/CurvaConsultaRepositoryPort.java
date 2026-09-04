package com.poccurves.api.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CurvaConsultaRepositoryPort {

    record VersaoCurvaRegistro(
            UUID versaoCurvaId,
            UUID definicaoCurvaId,
            String codigoCurva,
            String nomeCurva,
            String modoOrigem,
            LocalDate dataReferencia,
            String momentoCurva,
            int numeroVersao,
            String origemVersao,
            String estado,
            UUID execucaoCurvaId,
            Instant publicadoEm
    ) {}

    record VerticeRegistro(
            int prazoDiasUteis,
            Integer prazoDiasCorridos,
            LocalDate dataVencimento,
            BigDecimal taxa,
            BigDecimal fatorDesconto
    ) {}

    record ProcedenciaRegistro(
            UUID id,
            UUID versaoCurvaId,
            UUID execucaoCurvaId,
            String correlationId,
            int numeroVersaoDefinicao,
            String modeloCodigo,
            String checksumModelo,
            String referenciasInsumo,
            String hashConjuntoInsumos,
            Long loteIngestaoId,
            String arquivoCarga,
            String hashArquivo,
            String carregadoPor,
            String justificativa,
            String versaoMotor,
            Instant criadoEm
    ) {}

    record ValidacaoItemRegistro(
            String teste,
            String classificacao,
            String resultado,
            BigDecimal medidaObservada,
            BigDecimal limiteAplicado,
            String detalhe,
            Instant executadoEm
    ) {}

    Optional<VersaoCurvaRegistro> buscarVersaoPublicada(String codigoCurva, LocalDate dataReferencia, String momentoCurva);

    Optional<VersaoCurvaRegistro> buscarVersaoPorNumero(String codigoCurva, LocalDate dataReferencia, String momentoCurva, int numeroVersao);

    Optional<VersaoCurvaRegistro> buscarVersaoAsOf(String codigoCurva, LocalDate dataReferencia, String momentoCurva, Instant asOf);

    List<VerticeRegistro> listarVertices(UUID versaoCurvaId, int offset, int limit);

    int contarVertices(UUID versaoCurvaId);

    List<VerticeRegistro> buscarTodosVertices(UUID versaoCurvaId);

    List<VersaoCurvaRegistro> listarHistoricoVersoes(String codigoCurva, LocalDate dataReferencia, String momentoCurva);

    Optional<ProcedenciaRegistro> buscarProcedencia(UUID versaoCurvaId);

    List<ValidacaoItemRegistro> buscarValidacoes(UUID versaoCurvaId);
}
