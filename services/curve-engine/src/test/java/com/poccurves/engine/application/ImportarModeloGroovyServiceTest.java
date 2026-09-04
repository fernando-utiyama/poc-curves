package com.poccurves.engine.application;
import com.poccurves.engine.domain.construcao.ModeloConstrucaoException;
import com.poccurves.engine.domain.construcao.ModeloCurva;
import com.poccurves.engine.domain.curva.CurvaJuros;

import com.poccurves.engine.dto.EngineDtos.*;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ImportarModeloGroovyServiceTest {

    private final ModeloCurvaRepositoryPort modeloCurvaRepository = mock(ModeloCurvaRepositoryPort.class);
    private final ModeloConstrucaoPort groovyModeloConstrucao = mock(ModeloConstrucaoPort.class);
    private final ImportarModeloGroovyService service = new ImportarModeloGroovyService(modeloCurvaRepository, groovyModeloConstrucao);

    @Test
    void rejeitaCodigoVazio() {
        ImportarModeloGroovyRequest request = new ImportarModeloGroovyRequest("", "nome", "[]");
        assertThatThrownBy(() -> service.importar(request, "tester")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejeitaScriptVazio() {
        ImportarModeloGroovyRequest request = new ImportarModeloGroovyRequest("COD", "nome", "  ");
        assertThatThrownBy(() -> service.importar(request, "tester")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void recusaSeJaExisteModeloComOMesmoCodigo() {
        when(modeloCurvaRepository.buscarPorCodigo("COD")).thenReturn(Optional.of(mock(ModeloCurva.class)));
        ImportarModeloGroovyRequest request = new ImportarModeloGroovyRequest("COD", "nome", "[]");

        ImportarModeloResponse resposta = service.importar(request, "tester");

        assertThat(resposta.status()).isEqualTo("ERRO_COMPILACAO");
        verify(modeloCurvaRepository, never()).inserir(any());
    }

    @Test
    void persisteQuandoValidacaoPassa() {
        when(modeloCurvaRepository.buscarPorCodigo("COD")).thenReturn(Optional.empty());
        when(groovyModeloConstrucao.construir(any(), any())).thenReturn(mock(CurvaJuros.class));
        ImportarModeloGroovyRequest request = new ImportarModeloGroovyRequest("COD", "nome", "[]");

        ImportarModeloResponse resposta = service.importar(request, "tester");

        assertThat(resposta.status()).isEqualTo("VALIDO");
        assertThat(resposta.checksum()).isNotBlank();
        verify(modeloCurvaRepository).inserir(any());
    }

    @Test
    void naoPersisteQuandoFalhaDeCompilacao() {
        when(modeloCurvaRepository.buscarPorCodigo("COD")).thenReturn(Optional.empty());
        when(groovyModeloConstrucao.construir(any(), any()))
                .thenThrow(new ModeloConstrucaoException(ModeloConstrucaoException.Fase.COMPILACAO, "erro de sintaxe", null));
        ImportarModeloGroovyRequest request = new ImportarModeloGroovyRequest("COD", "nome", "isto nao compila {{{");

        ImportarModeloResponse resposta = service.importar(request, "tester");

        assertThat(resposta.status()).isEqualTo("ERRO_COMPILACAO");
        verify(modeloCurvaRepository, never()).inserir(any());
    }

    @Test
    void naoPersisteQuandoFalhaDeExecucao() {
        when(modeloCurvaRepository.buscarPorCodigo("COD")).thenReturn(Optional.empty());
        when(groovyModeloConstrucao.construir(any(), any()))
                .thenThrow(new ModeloConstrucaoException(ModeloConstrucaoException.Fase.EXECUCAO, "estourou em runtime", null));
        ImportarModeloGroovyRequest request = new ImportarModeloGroovyRequest("COD", "nome", "throw new RuntimeException()");

        ImportarModeloResponse resposta = service.importar(request, "tester");

        assertThat(resposta.status()).isEqualTo("ERRO_EXECUCAO_TESTE");
        verify(modeloCurvaRepository, never()).inserir(any());
    }
}
