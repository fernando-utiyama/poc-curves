package br.com.poc.application.model.scheduler;

import lombok.Data;

/**
 * Modelo que representa um parâmetro associado a uma `Tarefa`.
 *
 * Campos comuns:
 * - `nome`: nome do parâmetro (ex: url, method, header.Authorization)
 * - `valor`: valor do parâmetro
 * - `tipo`: tipo de dado ou categoria do parâmetro
 */
@Data
public class ParametroTarefa {
    private Long id;
    private String nome;
    private String valor;
    private String tipo;
}
