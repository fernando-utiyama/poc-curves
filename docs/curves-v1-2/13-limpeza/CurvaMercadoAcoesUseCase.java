package br.com.poc.application.port.in.usecase;

import br.com.poc.domain.cadastro.RespostaEngine;

import java.time.LocalDate;

/** Ações da curva numa data-base; a curva é identificada pelo nome. */
public interface CurvaMercadoAcoesUseCase {

    RespostaEngine construir(String nome, LocalDate dataBase, Boolean forcar, String fonte, String produto, String correlationId);

    RespostaEngine regravarInterpolada(String nome, LocalDate dataBase, String correlationId);

    RespostaEngine consultarVertices(String nome, LocalDate dataBase, String correlationId);

    RespostaEngine interpolar(String nome, LocalDate dataBase, String queryString, String correlationId);

    void excluirVertices(String nome, LocalDate dataBase);
}
