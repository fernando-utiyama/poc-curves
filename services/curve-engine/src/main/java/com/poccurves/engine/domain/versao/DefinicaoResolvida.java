package com.poccurves.engine.domain.versao;
import com.poccurves.engine.domain.validacao.LimiteValidacao;

import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

public record DefinicaoResolvida(
        UUID definicaoCurvaId,
        String codigo,
        String modoOrigem,
        UUID versaoDefinicaoCurvaId,
        int numeroVersaoDefinicao,
        UUID modeloCurvaId,
        List<String> vinculosFonte,
        List<String> dependeDe,
        LocalTime horarioLimitePublicacao,
        List<LimiteValidacao> limitesValidacao,
        String codigoCurvaImportadaIrmao
) {}
