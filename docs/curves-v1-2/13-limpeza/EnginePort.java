package br.com.poc.application.port.out;

import br.com.poc.domain.cadastro.RespostaEngine;

import java.time.LocalDate;

public interface EnginePort {

    RespostaEngine construir(String codigo, LocalDate dataBase, Boolean forcarRecalculo, String fonte, String produto, String correlationId);

    RespostaEngine regravarInterpolada(String codigo, LocalDate dataBase, String correlationId);

    RespostaEngine consultarVertices(String codigo, LocalDate dataBase, String correlationId);

    RespostaEngine interpolar(String codigo, LocalDate dataBase, String queryString, String correlationId);
}
