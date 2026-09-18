package com.poccurves.bff.application;

import com.poccurves.bff.dto.BffDtos.*;

import java.time.LocalDate;
import java.util.Optional;

public interface CurveApiPort {

    CatalogoResponse getCatalogo();

    Optional<CurvaMercadoDTO> getCurva(String ticker);

    Optional<CurvaDadosDTO> getVertices(String ticker, LocalDate dataReferencia);

    Optional<CurvaDadosDTO> getCurvaConstruida(String ticker, LocalDate dataReferencia);

    ComparacaoResponse compararCurvas(ComparacaoCurvasRequest request);
}
