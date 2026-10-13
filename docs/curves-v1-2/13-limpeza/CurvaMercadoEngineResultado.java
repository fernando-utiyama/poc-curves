package br.com.poc.application.port.in.usecase;

/** Resposta do engine repassada como veio: status HTTP e corpo JSON. */
public record CurvaMercadoEngineResultado(int status, String corpoJson) {
}
