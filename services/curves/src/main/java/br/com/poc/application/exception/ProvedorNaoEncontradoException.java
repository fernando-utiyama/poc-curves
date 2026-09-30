package br.com.poc.application.exception;

public class ProvedorNaoEncontradoException extends ExcecaoNegocio {

    public ProvedorNaoEncontradoException(String nomeProvedor) {
        super("Provedor não encontrado para nomeProvedor: " + nomeProvedor);
    }
}
