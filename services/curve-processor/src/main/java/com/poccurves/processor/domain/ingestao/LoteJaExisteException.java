package com.poccurves.processor.domain.ingestao;

/**
 * Sinaliza que {@link LoteIngestaoRepositoryPort#inserir} colidiu com um lote já existente
 * (restrição UNIQUE de lote_externo_id) — tradução de
 * {@code org.springframework.dao.DataIntegrityViolationException} feita no adaptador de
 * persistência, para que `application` nunca precise importar tipo de infraestrutura só para
 * reagir a essa corrida entre faixas concorrentes (tarefa 8.19 do backlog original).
 */
public class LoteJaExisteException extends RuntimeException {

    public LoteJaExisteException(String loteExternoId, Throwable causa) {
        super("lote já existe (corrida entre faixas concorrentes): loteExternoId=" + loteExternoId, causa);
    }
}
