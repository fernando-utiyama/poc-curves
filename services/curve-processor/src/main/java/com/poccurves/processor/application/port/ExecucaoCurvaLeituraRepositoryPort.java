package com.poccurves.processor.application.port;
import com.poccurves.processor.application.model.MomentoCurva;


import java.util.Optional;
import java.util.UUID;

public interface ExecucaoCurvaLeituraRepositoryPort {

    record ExecucaoCurvaResumo(UUID id, MomentoCurva momentoCurva) {}

    Optional<ExecucaoCurvaResumo> buscarPorCorrelacaoId(UUID correlacaoId);
}
