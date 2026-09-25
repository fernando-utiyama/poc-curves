package com.poccurves.engine.adapter.out.cache;

import com.poccurves.engine.dto.EngineDtos.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RedisCacheInterpolacaoAdapterTest {

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private RedisCacheInterpolacaoAdapter adapter;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        adapter = new RedisCacheInterpolacaoAdapter(redisTemplate);
    }

    @Test
    void buscar_comChaveAusente_retornaOptionalEmpty() {
        String chave = "interpolacao:PRE|2026-08-21|uuid|LINEAR|21";
        when(valueOperations.get(chave)).thenReturn(null);

        Optional<ItemInterpolacaoResultado> resultado = adapter.buscar(chave, 21);

        assertTrue(resultado.isEmpty());
    }

    @Test
    void buscar_comChavePresente_retornaItemDesserializado() {
        String chave = "interpolacao:PRE|2026-08-21|uuid|LINEAR|21";
        when(valueOperations.get(chave)).thenReturn("INTERPOLADO|13.50|0.9812|");

        Optional<ItemInterpolacaoResultado> resultadoOpt = adapter.buscar(chave, 21);

        assertTrue(resultadoOpt.isPresent());
        ItemInterpolacaoResultado item = resultadoOpt.get();
        assertEquals(21, item.prazoDiasUteis());
        assertEquals("13.50", item.taxa());
        assertEquals("0.9812", item.fatorDesconto());
        assertEquals("INTERPOLADO", item.status());
        assertNull(item.erroMensagem());
    }

    @Test
    void buscar_comErroMensagemContendoPipe_preservaMensagemCompleta() {
        String chave = "interpolacao:PRE|2026-08-21|uuid|LINEAR|999";
        String valorSerializado = "ERRO_FORA_INTERVALO|||prazo 999 fora do intervalo [10|500]";
        when(valueOperations.get(chave)).thenReturn(valorSerializado);

        Optional<ItemInterpolacaoResultado> resultadoOpt = adapter.buscar(chave, 999);

        assertTrue(resultadoOpt.isPresent());
        ItemInterpolacaoResultado item = resultadoOpt.get();
        assertEquals(999, item.prazoDiasUteis());
        assertNull(item.taxa());
        assertNull(item.fatorDesconto());
        assertEquals("ERRO_FORA_INTERVALO", item.status());
        assertEquals("prazo 999 fora do intervalo [10|500]", item.erroMensagem());
    }

    @Test
    void armazenar_chamaSetComValorSerializadoCorreto() {
        String chave = "interpolacao:PRE|2026-08-21|uuid|LINEAR|21";
        ItemInterpolacaoResultado item = new ItemInterpolacaoResultado(
                21,
                "13.50",
                "0.9812",
                "INTERPOLADO",
                "sem erro"
        );

        adapter.armazenar(chave, item);

        verify(valueOperations).set(chave, "INTERPOLADO|13.50|0.9812|sem erro");
    }

    @Test
    void buscar_quandoRedisLancaExcecao_retornaEmptySemPropagarExcecao() {
        when(valueOperations.get(any())).thenThrow(new RuntimeException("timeout simulado"));

        Optional<ItemInterpolacaoResultado> resultado = adapter.buscar("qualquer_chave", 21);

        assertTrue(resultado.isEmpty());
    }

    @Test
    void armazenar_quandoRedisLancaExcecao_retornaSemPropagarExcecao() {
        doThrow(new RuntimeException("timeout simulado")).when(valueOperations).set(any(), any());

        ItemInterpolacaoResultado item = new ItemInterpolacaoResultado(
                21,
                "13.50",
                "0.9812",
                "INTERPOLADO",
                null
        );

        assertDoesNotThrow(() -> adapter.armazenar("qualquer_chave", item));
    }
}
