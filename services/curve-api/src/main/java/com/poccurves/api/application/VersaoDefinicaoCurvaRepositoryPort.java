package com.poccurves.api.application;

import com.poccurves.api.domain.VersaoDefinicaoCurva;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VersaoDefinicaoCurvaRepositoryPort {

    void salvar(VersaoDefinicaoCurva v);

    void encerrarVigencia(UUID id, LocalDate vigenciaFim);

    Optional<VersaoDefinicaoCurva> buscarVigente(UUID definicaoCurvaId, LocalDate data);

    Optional<VersaoDefinicaoCurva> buscarMaisRecente(UUID definicaoCurvaId);

    Optional<VersaoDefinicaoCurva> buscarPorNumero(UUID definicaoCurvaId, int numeroVersao);

    List<VersaoDefinicaoCurva> listarPorDefinicao(UUID definicaoCurvaId);
}
