package com.poccurves.processor.domain.cargamanual;

/** O perfil do requisitante não é OPERADOR nem ADMINISTRADOR (tarefa 6.11). */
public final class PerfilNaoAutorizadoParaCargaException extends RuntimeException {

    public PerfilNaoAutorizadoParaCargaException(String perfil) {
        super("PERFIL_NAO_AUTORIZADO: perfil \"" + perfil + "\" não autorizado a fazer carga manual de curva — exigido OPERADOR ou ADMINISTRADOR");
    }
}
