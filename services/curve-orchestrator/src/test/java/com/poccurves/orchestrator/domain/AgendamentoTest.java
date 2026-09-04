package com.poccurves.orchestrator.domain;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgendamentoTest {

    @Test
    void fabricaValidaComDefinicaoCurva() {
        UUID definicaoId = UUID.randomUUID();
        Agendamento a = Agendamento.criar(
                definicaoId, null, MomentoCurva.INTRADIA, Faixa.ROTINA,
                "0 0 12 * * ?", "America/Sao_Paulo", 30, 60, "teste"
        );
        assertThat(a.id()).isNotNull();
        assertThat(a.definicaoCurvaId()).isEqualTo(definicaoId);
        assertThat(a.ativo()).isTrue();
    }

    @Test
    void expressaoCronInvalidaRejeitada() {
        assertThatThrownBy(() -> Agendamento.criar(
                UUID.randomUUID(), null, MomentoCurva.INTRADIA, Faixa.ROTINA,
                "invalido", "America/Sao_Paulo", 30, 60, "teste"
        )).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Expressão cron inválida");
    }

    @Test
    void fusoInvalidoRejeitado() {
        assertThatThrownBy(() -> Agendamento.criar(
                UUID.randomUUID(), null, MomentoCurva.INTRADIA, Faixa.ROTINA,
                "0 0 12 * * ?", "Fuso/Invalido", 30, 60, "teste"
        )).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Fuso horário inválido");
    }

    @Test
    void janelaIntervaloNaoPositivosRejeitados() {
        assertThatThrownBy(() -> Agendamento.criar(
                UUID.randomUUID(), null, MomentoCurva.INTRADIA, Faixa.ROTINA,
                "0 0 12 * * ?", "America/Sao_Paulo", 0, 60, "teste"
        )).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Janela de tentativa deve ser maior que zero");

        assertThatThrownBy(() -> Agendamento.criar(
                UUID.randomUUID(), null, MomentoCurva.INTRADIA, Faixa.ROTINA,
                "0 0 12 * * ?", "America/Sao_Paulo", 30, -5, "teste"
        )).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Intervalo de tentativa deve ser maior que zero");
    }

    @Test
    void exatamenteUmAlvoValidado() {
        // Dois vazios
        assertThatThrownBy(() -> Agendamento.criar(
                null, null, MomentoCurva.INTRADIA, Faixa.ROTINA,
                "0 0 12 * * ?", "America/Sao_Paulo", 30, 60, "teste"
        )).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("exatamente um alvo");

        // Dois preenchidos
        assertThatThrownBy(() -> Agendamento.criar(
                UUID.randomUUID(), "CONJUNTO", MomentoCurva.INTRADIA, Faixa.ROTINA,
                "0 0 12 * * ?", "America/Sao_Paulo", 30, 60, "teste"
        )).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("exatamente um alvo");
    }
}
