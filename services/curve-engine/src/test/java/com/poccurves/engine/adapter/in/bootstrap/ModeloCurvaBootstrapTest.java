package com.poccurves.engine.adapter.in.bootstrap;
import com.poccurves.engine.domain.construcao.ModeloCurva;

import com.poccurves.engine.application.ModeloCurvaRepositoryPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.ApplicationArguments;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ModeloCurvaBootstrapTest {

    @Mock
    private ModeloCurvaRepositoryPort modeloCurvaRepository;

    @InjectMocks
    private ModeloCurvaBootstrap bootstrap;

    @Test
    void naoRecriaSeJaExiste() {
        ModeloCurva existente = mock(ModeloCurva.class);
        when(modeloCurvaRepository.buscarPorCodigo("PRE_DI1_B3")).thenReturn(Optional.of(existente));

        bootstrap.run(mock(ApplicationArguments.class));

        verify(modeloCurvaRepository, never()).inserir(any());
    }

    @Test
    void criaSeNaoExistir() {
        when(modeloCurvaRepository.buscarPorCodigo("PRE_DI1_B3")).thenReturn(Optional.empty());

        bootstrap.run(mock(ApplicationArguments.class));

        ArgumentCaptor<ModeloCurva> captor = ArgumentCaptor.forClass(ModeloCurva.class);
        verify(modeloCurvaRepository).inserir(captor.capture());

        ModeloCurva salvo = captor.getValue();
        assertThat(salvo.codigo()).isEqualTo("PRE_DI1_B3");
        assertThat(salvo.tipo().name()).isEqualTo("BUILTIN");
    }
}
