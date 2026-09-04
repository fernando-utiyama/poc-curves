package com.poccurves.processor.application;
import com.poccurves.processor.domain.ingestao.LoteIngestao;


public interface NormalizedEventPort {

    void publicar(LoteIngestao lote);
}
