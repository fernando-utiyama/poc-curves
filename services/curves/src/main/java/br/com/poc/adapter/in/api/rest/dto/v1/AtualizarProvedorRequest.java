package br.com.poc.adapter.in.api.rest.dto.v1;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AtualizarProvedorRequest(@NotBlank @Size(max = 1024) String nomeProvedor,
                                 @Size(max = 1024) String descricao,
                                 @Size(max = 1024) String produto,
                                 @Size(max = 50) String nomeCompletoAtivoOuInstrumento) {
}
