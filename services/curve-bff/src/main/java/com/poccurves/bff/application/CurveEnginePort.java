package com.poccurves.bff.application;

import com.poccurves.bff.dto.BffDtos.*;

public interface CurveEnginePort {

    InterpolacaoResponse interpolar(String codigo, InterpolacaoRequest request);

    ImportarModeloResponse validarScriptGroovy(ImportarModeloGroovyRequest request);
}
