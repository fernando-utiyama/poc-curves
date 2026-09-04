package com.poccurves.processor.domain.cargamanual;
import com.poccurves.processor.domain.curva.VersaoCurva;


import java.util.List;

/**
 * Resultado de uma tentativa de publicação de carga manual: a versão
 * (PUBLICADA se aprovada, REPROVADA se algum teste bloqueante falhou, ou a
 * versão já existente se era recarga do mesmo arquivo — {@code recarga}
 * true nesse caso), e os resultados da bateria de validação (vazios só no
 * caso de recarga, já que a bateria não roda de novo).
 */
public record ResultadoPublicacaoCarga(
        VersaoCurva versao,
        List<ResultadoTesteCarga> resultadosValidacao,
        boolean recarga
) {
}
