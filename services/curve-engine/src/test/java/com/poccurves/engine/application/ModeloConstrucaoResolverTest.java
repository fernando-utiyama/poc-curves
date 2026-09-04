package com.poccurves.engine.application;
import com.poccurves.engine.domain.construcao.ModeloCurva;
import com.poccurves.engine.domain.curva.CurvaJuros;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ModeloConstrucaoResolverTest {

    @Test
    void despachaParaAPrimeiraImplementacaoQueSuporta() {
        ModeloCurva modelo = ModeloCurva.builtin("X", "x");
        CurvaJuros esperada = mock(CurvaJuros.class);

        ModeloConstrucaoPort naoSuporta = mock(ModeloConstrucaoPort.class);
        when(naoSuporta.suporta(modelo)).thenReturn(false);

        ModeloConstrucaoPort suporta = mock(ModeloConstrucaoPort.class);
        when(suporta.suporta(modelo)).thenReturn(true);
        when(suporta.construir(any(), any())).thenReturn(esperada);

        ModeloConstrucaoResolver resolver = new ModeloConstrucaoResolver(List.of(naoSuporta, suporta));

        assertThat(resolver.construir(modelo, List.of())).isSameAs(esperada);
    }

    @Test
    void lancaExcecaoQuandoNenhumaImplementacaoSuporta() {
        ModeloCurva modelo = ModeloCurva.builtin("X", "x");
        ModeloConstrucaoResolver resolver = new ModeloConstrucaoResolver(List.of());

        assertThatThrownBy(() -> resolver.construir(modelo, List.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("nenhuma implementação");
    }

    @Test
    void mesmaInstanciaDoResolverAlternaEntreBuiltinEGroovyEVoltaSemReiniciar() {
        ModeloCurva modeloBuiltin = ModeloCurva.builtin("PRE_DI1_B3", "modelo embutido");
        ModeloCurva modeloGroovy = ModeloCurva.importarGroovy("ALT", "modelo alternativo", "[[prazoDiasUteis: 21, taxa: 13.50]]", "checksum123", "autor");

        CurvaJuros curvaBuiltinEsperada = mock(CurvaJuros.class);
        CurvaJuros curvaGroovyEsperada = mock(CurvaJuros.class);

        ModeloConstrucaoPort implementacaoBuiltin = mock(ModeloConstrucaoPort.class);
        when(implementacaoBuiltin.suporta(modeloBuiltin)).thenReturn(true);
        when(implementacaoBuiltin.suporta(modeloGroovy)).thenReturn(false);
        when(implementacaoBuiltin.construir(eq(modeloBuiltin), any())).thenReturn(curvaBuiltinEsperada);

        ModeloConstrucaoPort implementacaoGroovy = mock(ModeloConstrucaoPort.class);
        when(implementacaoGroovy.suporta(modeloBuiltin)).thenReturn(false);
        when(implementacaoGroovy.suporta(modeloGroovy)).thenReturn(true);
        when(implementacaoGroovy.construir(eq(modeloGroovy), any())).thenReturn(curvaGroovyEsperada);

        ModeloConstrucaoResolver resolver = new ModeloConstrucaoResolver(List.of(implementacaoBuiltin, implementacaoGroovy));

        // curva aponta pro embutido, troca pro groovy, e volta pro embutido -- tudo na MESMA instancia
        // do resolver, sem recriar nada, provando que a troca e so uma decisao de dado a cada chamada
        assertThat(resolver.construir(modeloBuiltin, List.of())).isSameAs(curvaBuiltinEsperada);
        assertThat(resolver.construir(modeloGroovy, List.of())).isSameAs(curvaGroovyEsperada);
        assertThat(resolver.construir(modeloBuiltin, List.of())).isSameAs(curvaBuiltinEsperada);
    }
}

