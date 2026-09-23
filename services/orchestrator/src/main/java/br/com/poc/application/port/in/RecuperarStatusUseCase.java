package br.com.poc.application.port.in;

import br.com.poc.adapter.in.api.rest.dto.scheduler.RecuperarStatusResponseDto;

public interface RecuperarStatusUseCase {
    RecuperarStatusResponseDto recuperarStatusDetalhado(Long tarefaId);
}
