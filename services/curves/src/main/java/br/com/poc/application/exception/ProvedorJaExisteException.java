package br.com.poc.application.exception;

public class ProvedorJaExisteException extends ExcecaoNegocio {

    public ProvedorJaExisteException(String nomeProvedor) {
        super("Já existe um provedor cadastrado com nomeProvedor: " + nomeProvedor);
    }
}
