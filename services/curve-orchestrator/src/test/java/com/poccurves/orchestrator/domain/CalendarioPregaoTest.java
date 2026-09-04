package com.poccurves.orchestrator.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CalendarioPregaoTest {

    @Test
    void shouldReturnTrueForRegularWeekdayWithoutHoliday() {
        // 2026-08-24 é uma segunda-feira comum, sem feriado
        LocalDate diaUtil = LocalDate.of(2026, 8, 24);
        assertThat(CalendarioPregao.ehDiaDePregao(diaUtil)).isTrue();
    }

    @Test
    void shouldReturnFalseForWeekends() {
        LocalDate sabado = LocalDate.of(2026, 8, 22);
        LocalDate domingo = LocalDate.of(2026, 8, 23);

        assertThat(CalendarioPregao.ehDiaDePregao(sabado)).isFalse();
        assertThat(CalendarioPregao.ehDiaDePregao(domingo)).isFalse();
    }

    @Test
    void shouldReturnFalseForFixedHolidays() {
        // Natal em 2026 cai em uma sexta-feira
        LocalDate natal = LocalDate.of(2026, 12, 25);
        assertThat(CalendarioPregao.ehDiaDePregao(natal)).isFalse();

        // Consciência Negra (20 de novembro de 2026, sexta-feira)
        LocalDate conscienciaNegra = LocalDate.of(2026, 11, 20);
        assertThat(CalendarioPregao.ehDiaDePregao(conscienciaNegra)).isFalse();

        // Tiradentes (21 de abril de 2026, terça-feira)
        LocalDate tiradentes = LocalDate.of(2026, 4, 21);
        assertThat(CalendarioPregao.ehDiaDePregao(tiradentes)).isFalse();
    }

    @Test
    void shouldReturnFalseForCalculatedMobileHolidaysIn2026() {
        // Cálculo do Domingo de Páscoa para 2026 pelo algoritmo de Meeus/Jones/Butcher:
        // a = 2026 % 19 = 12, b = 20, c = 26, d = 5, e = 0, f = 1, g = 6
        // h = (19*12 + 20 - 5 - 6 + 15) % 30 = 252 % 30 = 12
        // i = 26 / 4 = 6, k = 26 % 4 = 2
        // l = (32 + 0 + 12 - 12 - 2) % 7 = 30 % 7 = 2
        // m = (12 + 132 + 44) / 451 = 0
        // mes = (12 + 2 - 0 + 114) / 31 = 4 (Abril)
        // dia = (128 % 31) + 1 = 5
        // -> Domingo de Páscoa = 2026-04-05
        //
        // Feriados móveis relativos:
        // - Carnaval (segunda)   = 2026-04-05 - 48 dias = 2026-02-16
        // - Carnaval (terça)     = 2026-04-05 - 47 dias = 2026-02-17
        // - Sexta-feira Santa    = 2026-04-05 - 2 dias  = 2026-04-03
        // - Corpus Christi       = 2026-04-05 + 60 dias = 2026-06-04

        LocalDate sextaFeiraSanta = LocalDate.of(2026, 4, 3);
        LocalDate carnavalSegunda = LocalDate.of(2026, 2, 16);
        LocalDate carnavalTerca = LocalDate.of(2026, 2, 17);
        LocalDate corpusChristi = LocalDate.of(2026, 6, 4);

        assertThat(CalendarioPregao.ehDiaDePregao(sextaFeiraSanta)).isFalse();
        assertThat(CalendarioPregao.ehDiaDePregao(carnavalSegunda)).isFalse();
        assertThat(CalendarioPregao.ehDiaDePregao(carnavalTerca)).isFalse();
        assertThat(CalendarioPregao.ehDiaDePregao(corpusChristi)).isFalse();
    }

    @Test
    void shouldReturnTrueForMaundyThursday() {
        // Quinta-feira Santa (2026-04-02, véspera da Sexta-feira Santa): NÃO é feriado (Resolução ANBIMA 2.516/2000)
        LocalDate quintaFeiraSanta = LocalDate.of(2026, 4, 2);
        assertThat(CalendarioPregao.ehDiaDePregao(quintaFeiraSanta)).isTrue();
    }

    @Test
    void shouldThrowIllegalArgumentExceptionWhenYearIsOutsideSupportedRange() {
        LocalDate ano2040 = LocalDate.of(2040, 1, 1);
        assertThatThrownBy(() -> CalendarioPregao.ehDiaDePregao(ano2040))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("[2020, 2035]")
                .hasMessage("ano 2040 fora do intervalo suportado pelo calendário [2020, 2035]");

        LocalDate ano2019 = LocalDate.of(2019, 12, 31);
        assertThatThrownBy(() -> CalendarioPregao.ehDiaDePregao(ano2019))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("[2020, 2035]")
                .hasMessage("ano 2019 fora do intervalo suportado pelo calendário [2020, 2035]");
    }

    @Test
    void shouldThrowNullPointerExceptionWhenDataIsNull() {
        assertThatThrownBy(() -> CalendarioPregao.ehDiaDePregao(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("data não pode ser nula");
    }

    @Test
    void shouldReturnAllFixedAndMobileHolidaysForYear() {
        Set<LocalDate> feriados2026 = CalendarioPregao.feriadosDoAno(2026);

        assertThat(feriados2026).containsExactlyInAnyOrder(
                // Fixos
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 4, 21),
                LocalDate.of(2026, 5, 1),
                LocalDate.of(2026, 9, 7),
                LocalDate.of(2026, 10, 12),
                LocalDate.of(2026, 11, 2),
                LocalDate.of(2026, 11, 15),
                LocalDate.of(2026, 11, 20),
                LocalDate.of(2026, 12, 25),
                // Móveis
                LocalDate.of(2026, 2, 16), // Carnaval segunda
                LocalDate.of(2026, 2, 17), // Carnaval terça
                LocalDate.of(2026, 4, 3),  // Sexta-feira Santa
                LocalDate.of(2026, 6, 4)   // Corpus Christi
        );
    }

    @Test
    void shouldAcceptDatesAtSupportedRangeBoundaries() {
        // 2020-01-02 foi quinta-feira útil (primeiro pregão de 2020)
        LocalDate limiteInferior = LocalDate.of(2020, 1, 2);
        assertThat(CalendarioPregao.ehDiaDePregao(limiteInferior)).isTrue();

        // 2035-12-31 é segunda-feira útil
        LocalDate limiteSuperior = LocalDate.of(2035, 12, 31);
        assertThat(CalendarioPregao.ehDiaDePregao(limiteSuperior)).isTrue();
    }

    @Test
    void shouldCountBusinessDaysExcludingWeekendsAndHolidays() {
        // De 23/Dez/2026 (Qua) a 29/Dez/2026 (Ter) exclusive.
        // 23: Qua (útil)
        // 24: Qui (útil)
        // 25: Sex (feriado Natal)
        // 26: Sáb (fim de semana)
        // 27: Dom (fim de semana)
        // 28: Seg (útil)
        LocalDate inicio = LocalDate.of(2026, 12, 23);
        LocalDate fim = LocalDate.of(2026, 12, 29);

        long diasUteis = CalendarioPregao.diasUteisEntre(inicio, fim);
        assertThat(diasUteis).isEqualTo(3);
    }

    @Test
    void shouldReturnZeroForEmptyIntervalInDiasUteisEntre() {
        LocalDate data = LocalDate.of(2026, 1, 2);
        long diasUteis = CalendarioPregao.diasUteisEntre(data, data);
        assertThat(diasUteis).isZero();
    }

    @Test
    void shouldThrowIllegalArgumentExceptionWhenDataFimIsBeforeDataInicioInDiasUteisEntre() {
        LocalDate inicio = LocalDate.of(2026, 1, 10);
        LocalDate fim = LocalDate.of(2026, 1, 2);

        assertThatThrownBy(() -> CalendarioPregao.diasUteisEntre(inicio, fim))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("não pode ser anterior a dataInicio");
    }

    @Test
    void shouldThrowNullPointerExceptionWhenArgumentsAreNullInDiasUteisEntre() {
        LocalDate data = LocalDate.of(2026, 1, 2);

        assertThatThrownBy(() -> CalendarioPregao.diasUteisEntre(null, data))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("dataInicio não pode ser nula");

        assertThatThrownBy(() -> CalendarioPregao.diasUteisEntre(data, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("dataFim não pode ser nula");
    }
}
