package com.poccurves.processor.domain.cargamanual;
import com.poccurves.processor.domain.curva.VerticeCurva;


import java.util.List;

/**
 * Contrato de um teste de validação de consistência sobre os vértices de
 * uma carga manual de curva. Testável sem banco: opera só sobre a lista de
 * vértices já lida (CSV ou XLSX), antes de qualquer persistência.
 */
public interface TesteValidacaoCarga {

    String identificador();

    ResultadoTesteCarga executar(List<VerticeCurva> vertices);
}
