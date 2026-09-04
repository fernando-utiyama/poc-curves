package com.poccurves.processor.domain.curva;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProcedenciaCurvaTest {

    @Test
    void importadaPreencheLoteEDeixaCamposDeCargaNulos() {
        UUID execucaoCurvaId = UUID.randomUUID();

        ProcedenciaCurva procedencia = ProcedenciaCurva.importada(execucaoCurvaId, 42L, "ref", "hash-conjunto");

        assertThat(procedencia.loteIngestaoId()).isEqualTo(42L);
        assertThat(procedencia.arquivoCarga()).isNull();
        assertThat(procedencia.carregadoPor()).isNull();
    }

    @Test
    void carregadaExigeTodosOsCamposDeCarga() {
        UUID execucaoCurvaId = UUID.randomUUID();

        ProcedenciaCurva procedencia = ProcedenciaCurva.carregada(execucaoCurvaId, "curva.csv", "hash-arquivo", "operador.risco", "correção de cadastro");

        assertThat(procedencia.arquivoCarga()).isEqualTo("curva.csv");
        assertThat(procedencia.loteIngestaoId()).isNull();
    }

    @Test
    void carregadaRecusaJustificativaVazia() {
        assertThatThrownBy(() -> ProcedenciaCurva.carregada(UUID.randomUUID(), "curva.csv", "hash", "operador", ""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void recusaExecucaoCurvaIdNulo() {
        assertThatThrownBy(() -> ProcedenciaCurva.importada(null, 1L, "ref", "hash"))
                .isInstanceOf(NullPointerException.class);
    }
}
