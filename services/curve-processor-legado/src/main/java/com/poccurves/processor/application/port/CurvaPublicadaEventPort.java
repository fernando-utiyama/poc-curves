package com.poccurves.processor.application.port;
import com.poccurves.processor.application.model.VersaoCurva;


public interface CurvaPublicadaEventPort {

    /**
     * @throws IllegalArgumentException se versao não estiver em estado PUBLICADA
     */
    void publicar(String curveCode, VersaoCurva versao, int vertexCount);
}
