package com.poccurves.processor.application;

import com.poccurves.processor.domain.VersaoCurva;

public interface CurvaPublicadaEventPort {

    /**
     * @throws IllegalArgumentException se versao não estiver em estado PUBLICADA
     */
    void publicar(String curveCode, VersaoCurva versao, int vertexCount);
}
