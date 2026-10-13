package br.com.poc.application.port.in.usecase;

import java.time.LocalDate;

/** Ações da curva numa data-base; a curva é identificada pelo nome. */
public interface CurvaMercadoAcoesUseCase {

    CurvaMercadoEngineResultado construir(String nome, LocalDate dataBase, Boolean forcar, String fonte, String produto, String correlationId);

    CurvaMercadoEngineResultado regravarInterpolada(String nome, LocalDate dataBase, String correlationId);

    CurvaMercadoEngineResultado consultarVertices(String nome, LocalDate dataBase, String correlationId);

    CurvaMercadoEngineResultado interpolar(String nome, LocalDate dataBase, String queryString, String correlationId);

    void excluirVertices(String nome, LocalDate dataBase);
}
