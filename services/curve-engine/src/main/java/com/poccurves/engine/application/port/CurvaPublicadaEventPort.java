package com.poccurves.engine.application.port;
import com.poccurves.engine.application.model.ResultadoTeste;
import com.poccurves.engine.application.model.VersaoCurva;


import java.util.List;
import java.util.UUID;

public interface CurvaPublicadaEventPort {
    void publicar(String curveCode, VersaoCurva versao, int vertexCount, List<ResultadoTeste> resultadosValidacao);
    void notificarFalha(UUID executionId, String motivo);
}
