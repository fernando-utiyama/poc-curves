package br.com.poc.application.dto.v1;

public record CriarProvedorCommand(
        String nomeProvedor,
        String descricao,
        String produto,
        String nomeCompletoAtivoOuInstrumento
) {
}
