package com.poccurves.engine.application.port;
import com.poccurves.engine.application.model.InsumoDI1;


import java.time.LocalDate;
import java.util.List;

public interface InsumoDI1RepositoryPort {
    List<InsumoDI1> buscarInsumosDI1(List<String> conjuntosDados, LocalDate dataReferencia);
}
