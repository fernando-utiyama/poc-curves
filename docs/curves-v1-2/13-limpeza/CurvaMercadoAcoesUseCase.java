package br.com.poc.application.port.in.usecase;

import java.time.LocalDate;

/** Ações da curva numa data-base; a curva é identificada pelo nome. */
public interface CurvaMercadoAcoesUseCase {

    /** Resposta do engine repassada como veio: status HTTP e corpo JSON. */
    record RespostaAcao(int status, String corpoJson) {}

    RespostaAcao construir(String nome, LocalDate dataBase, Boolean forcar, String fonte, String produto, String correlationId);

    RespostaAcao regravarInterpolada(String nome, LocalDate dataBase, String correlationId);

    RespostaAcao consultarVertices(String nome, LocalDate dataBase, String correlationId);

    RespostaAcao interpolar(String nome, LocalDate dataBase, String queryString, String correlationId);

    void excluirVertices(String nome, LocalDate dataBase);
}
