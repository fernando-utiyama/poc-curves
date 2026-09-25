package com.poccurves.engine.application.exception;

/**
 * Falha ao construir uma curva a partir de um modelo (embutido ou GROOVY).
 * {@link Fase} distingue erro de compilação (script Groovy inválido) de erro
 * de execução (script compilou mas falhou/estourou tempo/violou a contenção
 * ao rodar), usado pelo endpoint de importação para classificar
 * ERRO_COMPILACAO vs. ERRO_EXECUCAO_TESTE.
 */
public class ModeloConstrucaoException extends RuntimeException {

    public enum Fase { COMPILACAO, EXECUCAO }

    private final Fase fase;

    public ModeloConstrucaoException(Fase fase, String mensagem, Throwable causa) {
        super(mensagem, causa);
        this.fase = fase;
    }

    public Fase fase() {
        return fase;
    }
}
