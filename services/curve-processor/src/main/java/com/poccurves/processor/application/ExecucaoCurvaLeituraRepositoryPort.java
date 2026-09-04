package com.poccurves.processor.application;
import com.poccurves.processor.domain.curva.MomentoCurva;


import java.util.Optional;
import java.util.UUID;

public interface ExecucaoCurvaLeituraRepositoryPort {

    record ExecucaoCurvaResumo(UUID id, MomentoCurva momentoCurva) {}

    Optional<ExecucaoCurvaResumo> buscarPorCorrelacaoId(UUID correlacaoId);
}
