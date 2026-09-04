package com.poccurves.processor.domain.cargamanual;
import com.poccurves.processor.domain.curva.VerticeCurva;


import java.util.List;

/**
 * Sempre NAO_APLICAVEL nesta versão (tarefa 6.8 do backlog): compararia a
 * curva carregada manualmente contra uma curva de referência calibrada
 * (ex.: a mesma curva calculada pelo curve-engine no mesmo dia) para
 * detectar desvio anormal — mas isso depende de um insumo de calibração
 * (a curva de referência) que este serviço não tem acesso nem forma de
 * obter hoje: não há integração de leitura entre curve-processor e as
 * curvas publicadas pelo curve-engine. Registrado como NAO_APLICAVEL, não
 * como aprovado, para não mascarar a ausência do teste como se ele
 * tivesse rodado e passado.
 */
public final class TesteAderenciaCurvaReferencia implements TesteValidacaoCarga {

    @Override
    public String identificador() {
        return "ADERENCIA_CURVA_REFERENCIA";
    }

    @Override
    public ResultadoTesteCarga executar(List<VerticeCurva> vertices) {
        return new ResultadoTesteCarga(
                identificador(),
                Classificacao.AVISO,
                ResultadoValidacaoCarga.NAO_APLICAVEL,
                null,
                null,
                "requer curva de referência calibrada como insumo — não disponível para o curve-processor nesta versão");
    }
}
