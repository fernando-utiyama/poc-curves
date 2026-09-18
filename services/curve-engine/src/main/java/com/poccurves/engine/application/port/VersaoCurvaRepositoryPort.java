package com.poccurves.engine.application.port;
import com.poccurves.engine.application.model.MomentoCurva;
import com.poccurves.engine.application.model.VersaoCurva;


import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/**
 * Leitura de {@code versao_curva} (schema antigo) — só as curvas IMPORTED (ex. B3_CURVA_PRE, que
 * o curve-processor publica direto nessa tabela) ainda usam este caminho; a construção
 * BOOTSTRAPPED (DI1) que escrevia aqui foi removida (limpeza de BVBG.086/BVBG.028). Só leitura
 * porque curve-engine nunca escreveu versao_curva por conta própria além do que já foi removido —
 * quem publica é o curve-processor.
 */
public interface VersaoCurvaRepositoryPort {
    Optional<VersaoCurva> buscarVersaoVigentePublicada(UUID definicaoCurvaId, LocalDate dataReferencia, MomentoCurva momentoCurva);
    Optional<VersaoCurva> buscarPorNumeroVersao(UUID definicaoCurvaId, LocalDate dataReferencia, MomentoCurva momentoCurva, int numeroVersao);
}
