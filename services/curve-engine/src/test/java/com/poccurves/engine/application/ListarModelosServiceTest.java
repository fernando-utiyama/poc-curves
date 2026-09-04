package com.poccurves.engine.application;
import com.poccurves.engine.domain.construcao.EstadoModelo;
import com.poccurves.engine.domain.construcao.ModeloCurva;

import com.poccurves.engine.dto.EngineDtos.*;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ListarModelosServiceTest {

    @Test
    void listaTodosOsModelosMapeadosParaDTO() {
        ModeloCurvaRepositoryPort repository = mock(ModeloCurvaRepositoryPort.class);
        ModeloCurva builtin = ModeloCurva.builtin("PRE_DI1_B3", "Modelo Padrão");
        when(repository.listarTodos()).thenReturn(List.of(builtin));

        ListarModelosService service = new ListarModelosService(repository);
        ModelosResponse resposta = service.listarModelos();

        assertThat(resposta.modelos()).hasSize(1);
        var dto = resposta.modelos().get(0);
        assertThat(dto.codigo()).isEqualTo("PRE_DI1_B3");
        assertThat(dto.tipo()).isEqualTo("BUILTIN");
        assertThat(dto.estado()).isEqualTo(EstadoModelo.ATIVO.name());
    }
}
