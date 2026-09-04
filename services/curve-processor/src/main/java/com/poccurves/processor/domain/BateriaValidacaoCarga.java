package com.poccurves.processor.domain;

import java.util.List;

/**
 * Roda todos os {@link TesteValidacaoCarga} registrados sobre os vértices
 * de uma carga manual. Reprovada (não publica) se qualquer teste
 * BLOQUEANTE resultar REPROVADO — testes AVISO ou NAO_APLICAVEL nunca
 * impedem a publicação (tarefa 6.7/6.8 do backlog).
 */
public class BateriaValidacaoCarga {

    private final List<TesteValidacaoCarga> testes;

    public BateriaValidacaoCarga() {
        this.testes = List.of(
                new TesteTaxasNaoNegativas(),
                new TesteAderenciaCurvaReferencia()
        );
    }

    public List<ResultadoTesteCarga> executar(List<VerticeCurva> vertices) {
        return testes.stream().map(teste -> teste.executar(vertices)).toList();
    }

    /** Reprovada se algum teste BLOQUEANTE resultou REPROVADO. AVISO/NAO_APLICAVEL nunca bloqueiam. */
    public boolean aprovada(List<ResultadoTesteCarga> resultados) {
        return resultados.stream().noneMatch(r ->
                r.classificacao() == Classificacao.BLOQUEANTE
                        && r.resultado() == ResultadoValidacaoCarga.REPROVADO);
    }
}
