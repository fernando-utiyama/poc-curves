package com.poccurves.processor.application;
import com.poccurves.processor.domain.curva.MomentoCurva;
import com.poccurves.processor.domain.curva.VersaoCurva;


import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface VersaoCurvaRepositoryPort {

    void inserir(VersaoCurva versao);

    void atualizar(VersaoCurva versao);

    Optional<Integer> buscarMaiorNumeroVersao(UUID definicaoCurvaId, LocalDate dataReferencia, MomentoCurva momentoCurva);

    Optional<VersaoCurva> buscarPublicadaAtual(UUID definicaoCurvaId, LocalDate dataReferencia, MomentoCurva momentoCurva);

    Optional<VersaoCurva> buscarPorId(UUID id);
}
