package com.poccurves.engine.application;
import com.poccurves.engine.domain.validacao.ResultadoTeste;
import com.poccurves.engine.domain.versao.VersaoCurva;


import java.util.List;
import java.util.UUID;

public interface CurvaPublicadaEventPort {
    void publicar(String curveCode, VersaoCurva versao, int vertexCount, List<ResultadoTeste> resultadosValidacao);
    void notificarFalha(UUID executionId, String motivo);
}
