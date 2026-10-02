package br.com.poc.application.port.in;

import br.com.poc.adapter.in.api.rest.dto.scheduler.RecuperarStatusResponseDto;

import java.util.List;

public interface RecuperarStatusUseCase {
    List<RecuperarStatusResponseDto> listarStatusDetalhado();
    RecuperarStatusResponseDto recuperarStatusDetalhado(Long tarefaId);
}
