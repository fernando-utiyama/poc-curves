package br.com.poc.domain.cadastro;

/** Resposta do engine repassada como veio: status HTTP e corpo JSON. */
public record RespostaEngine(int status, String corpoJson) {
}
