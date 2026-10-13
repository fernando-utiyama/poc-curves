package br.com.poc.application.port.in.usecase;

/** Resposta do engine repassada como veio: status HTTP e corpo JSON. */
public record RespostaAcao(int status, String corpoJson) {
}
