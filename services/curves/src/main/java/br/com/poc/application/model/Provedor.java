package br.com.poc.application.model;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@AllArgsConstructor
@EqualsAndHashCode(of = "nomeProvedor")
public class Provedor {

    private final String nomeProvedor;
    private final String descricao;
    private final String produto;
    private final String nomeCompletoAtivoOuInstrumento;

    public Provedor atualizar(String nomeProvedor,
                              String descricao,
                              String produto,
                              String nomeCompletoAtivoOuInstrumento) {
        return new Provedor(
            nomeProvedor,
            descricao,
            produto,
            nomeCompletoAtivoOuInstrumento
        );
    }
}
