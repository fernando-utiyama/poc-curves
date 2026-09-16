package com.poccurves.processor.application.port;

import com.poccurves.processor.application.model.B3TaxaSwapParser.VerticeTaxaSwap;

import java.time.LocalDate;
import java.util.List;

/**
 * Escrita na tabela legada {@code tBtrsCurvaPrimr} (openspec/changes/legado-schema-curvas-mercado,
 * migração V22/V23) — destino das curvas TS B3 (DCL/PTX/INP/DPL), substituindo
 * ponto_dado_mercado/versao_curva/vertice_curva só para esses datasets.
 */
public interface BtrsCurvaPrimrRepositoryPort {

    /**
     * Substitui todos os vértices de {@code tickerIndcd}/{@code dataReferencia} pelos informados —
     * apaga o que já existir para essa curva/data antes de inserir, tornando a operação
     * idempotente por natureza (reprocessar a mesma data/curva apenas substitui, nunca duplica;
     * não há chave natural na tabela legada — cldtfdUnic é um id arbitrário gerado por sequence,
     * não a (curva, data, prazo) — então delete-then-insert é a forma mais simples de evitar
     * acúmulo de linha duplicada numa reentrega do Kafka).
     */
    void substituirVertices(String tickerIndcd, LocalDate dataReferencia, List<VerticeTaxaSwap> vertices);
}
