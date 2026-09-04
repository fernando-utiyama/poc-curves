package com.poccurves.orchestrator.domain;

/**
 * Espelha o CHECK constraint ck_execucao_curva_faixa de db/migration/V3__execucao_curva.sql,
 * e as três faixas de ingestão isoladas por consumidor Kafka (rotina/prioritária/massa)
 * do catálogo em contracts/events/topics.yaml.
 */
public enum Faixa {
    ROTINA,
    PRIORITARIA,
    MASSA
}
