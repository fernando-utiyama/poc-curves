package com.poccurves.engine.application.port;
import com.poccurves.engine.application.model.MomentoCurva;
import com.poccurves.engine.application.model.VersaoCurva;


import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface VersaoCurvaRepositoryPort {
    void inserir(VersaoCurva versao);
    void atualizar(VersaoCurva versao);
    Optional<VersaoCurva> buscarPorId(UUID id);
    Optional<VersaoCurva> buscarVersaoVigentePublicada(UUID definicaoCurvaId, LocalDate dataReferencia, MomentoCurva momentoCurva);
    Optional<VersaoCurva> buscarUltimaVersaoPublicadaAnterior(UUID definicaoCurvaId, LocalDate dataReferenciaAntesDe, MomentoCurva momentoCurva);
    int proximoNumeroVersao(UUID definicaoCurvaId, LocalDate dataReferencia, MomentoCurva momentoCurva);
    Optional<VersaoCurva> buscarPorNumeroVersao(UUID definicaoCurvaId, LocalDate dataReferencia, MomentoCurva momentoCurva, int numeroVersao);
    Optional<VersaoCurva> buscarPorExecucaoCurvaId(UUID execucaoCurvaId);
}
