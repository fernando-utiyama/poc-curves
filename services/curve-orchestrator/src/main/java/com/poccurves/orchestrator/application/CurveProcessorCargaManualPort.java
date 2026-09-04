package com.poccurves.orchestrator.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface CurveProcessorCargaManualPort {

    record ErroCargaInterno(int numeroLinha, String coluna, String mensagem) {}

    record ItemValidacaoInterno(
            String identificador,
            String classificacao,
            String resultado,
            BigDecimal medidaObservada,
            BigDecimal limiteAplicado,
            String detalhe
    ) {}

    record RespostaCargaInterna(
            String status,
            String mensagem,
            List<ErroCargaInterno> errosLeitura,
            List<ItemValidacaoInterno> validacoes,
            UUID versionId,
            Integer versionNumber
    ) {}

    /** @throws com.poccurves.orchestrator.domain.IntegracaoIndisponivelException em falha de transporte */
    RespostaCargaInterna carregar(
            String codigo, LocalDate dataReferencia, String momento, String justificativa,
            String carregadoPor, UUID execucaoCurvaId, byte[] arquivoBytes, String nomeArquivo);
}
