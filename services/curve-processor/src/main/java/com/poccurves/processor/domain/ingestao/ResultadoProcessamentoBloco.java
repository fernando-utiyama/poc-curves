package com.poccurves.processor.domain.ingestao;
import com.poccurves.processor.domain.cargamanual.DivergenciaValor;

import java.util.List;

/**
 * Resultado do processamento de um bloco: o estado atualizado do lote depois
 * do commit, e as divergências detectadas neste bloco especificamente
 * (não do lote inteiro — só o que este bloco regravou diferente).
 */
public record ResultadoProcessamentoBloco(LoteIngestao lote, List<DivergenciaValor> divergenciasNoBloco) {
}
