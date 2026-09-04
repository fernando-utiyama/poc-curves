package com.poccurves.bff.application;

import com.poccurves.bff.dto.BffDtos.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

public interface CurveApiPort {

    CatalogoResponse getCatalogo(String codigo, String modoOrigem, String estado, int pagina, int tamanho);

    DefinicaoCurvaDTO getDefinicaoCurva(String codigo);

    DefinicaoCurvaDTO criarDefinicaoCurva(String codigo, CriarOuAtualizarDefinicaoCurvaRequest req);

    DefinicaoCurvaDTO atualizarDefinicaoCurva(String codigo, CriarOuAtualizarDefinicaoCurvaRequest req);

    byte[] downloadModeloCarga(String codigo, String formato);

    Optional<CurvaViewerResponse> getCurvaPublicada(
            String codigo,
            LocalDate dataReferencia,
            String momento,
            Integer versao,
            Instant asOf
    );

    ComparacaoResponse compararCurvas(ComparacaoCurvasRequest request);
}
