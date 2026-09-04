package com.poccurves.orchestrator.application;

import com.poccurves.orchestrator.domain.DefinicaoConsumidora;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DefinicaoCurvaConsultaRepositoryPort {
    List<DefinicaoConsumidora> buscarDefinicoesBootstrappedQueConsomem(String conjuntoDados, LocalDate dataReferencia);
    Optional<UUID> buscarIdPorCodigo(String codigo);
}
