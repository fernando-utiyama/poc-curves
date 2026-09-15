package com.poccurves.processor.application.port;
import com.poccurves.processor.application.model.LoteIngestao;


public interface NormalizedEventPort {

    void publicar(LoteIngestao lote);
}
