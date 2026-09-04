package com.poccurves.engine.application.model;

import java.math.BigDecimal;

public record LimiteValidacao(String teste, Classificacao classificacao, BigDecimal limite) {

    public LimiteValidacao {
        if (teste == null || teste.isBlank()) {
            throw new IllegalArgumentException("Identificador do teste não pode ser nulo ou vazio");
        }
        if (classificacao == null) {
            throw new IllegalArgumentException("Classificação não pode ser nula para o teste '" + teste + "'");
        }
        if (limite == null) {
            throw new IllegalArgumentException("Limite não pode ser nulo para o teste '" + teste + "'");
        }
    }

    public static LimiteValidacao desserializar(String teste, String classificacaoTexto, String limiteTexto) {
        if (teste == null || teste.isBlank()) {
            throw new IllegalArgumentException("Identificador do teste não pode ser vazio");
        }

        Classificacao classificacao;
        try {
            if (classificacaoTexto == null || classificacaoTexto.isBlank()) {
                throw new IllegalArgumentException("vazia");
            }
            classificacao = Classificacao.valueOf(classificacaoTexto.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Classificação inválida para o teste '" + teste + "': " + classificacaoTexto);
        }

        BigDecimal limite;
        try {
            if (limiteTexto == null || limiteTexto.isBlank()) {
                throw new IllegalArgumentException("vazio");
            }
            limite = new BigDecimal(limiteTexto);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Limite numérico inválido para o teste '" + teste + "': " + limiteTexto);
        }

        return new LimiteValidacao(teste, classificacao, limite);
    }
}
