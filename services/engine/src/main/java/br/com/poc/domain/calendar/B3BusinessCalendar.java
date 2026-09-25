package br.com.poc.domain.calendar;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Implementação nativa do calendário oficial de negociação e liquidação da B3 (Brasil, Bolsa, Balcão).
 *
 * <p>Para instrumentos de juros (DI, DAP, Casado, Derivativos e Títulos Públicos negociados no Brasil),
 * a convenção oficial adotada pela B3 e pela Resolução CMN/Bacen segue os feriados bancários nacionais
 * (dias úteis para fins de operações no mercado financeiro):
 * <ul>
 *  <li>Feriados Nacionais Fixos:
 *      Confraternização Universal (01/01), Tiradentes (21/04), Dia do Trabalho (01/05),
 *      Independência (07/09), N. Sra. Aparecida (12/10), Finados (02/11), Proclamação da República (15/11),
 *      Dia da Consciência Negra (20/11 - Lei nº 14.759/2023) e Natal (25/12).</li>
 *  <li>Feriados Móveis Oficiais (apurados pelo Algoritmo de Páscoa de Meeus/Jones/Butcher):
 *      Segunda-feira de Carnaval (-48d), Terça-feira de Carnaval (-47d),
 *      Sexta-feira Santa / Paixão de Cristo (-2d) e Corpus Christi (+60d).</li>
 * </ul>
 * </p>
 */
public class B3BusinessCalendar implements BusinessCalendar {

    public static final String CALENDAR_NAME = "B3";
    private final Set<LocalDate> feriadosExtras;

    public B3BusinessCalendar() { this(Collections.emptySet()); }

    public B3BusinessCalendar(Set<LocalDate> feriadosExtras) {
        this.feriadosExtras = feriadosExtras != null ? new HashSet<>(feriadosExtras) : Collections.emptySet();
    }

    @Override
    public String getCalendarName() { return CALENDAR_NAME; }

    @Override
    public boolean isDiaUtil(LocalDate data) {
        if (data == null) {
            return false;
        }

        DayOfWeek dow = data.getDayOfWeek();
        if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) {
            return false;
        }

        if (feriadosExtras.contains(data)) {
            return false;
        }

        return !isFeriadoB3Padrao(data);
    }

    @Override
    public int contarDiasUteis(LocalDate dataInicio, LocalDate dataFim) {
        if (dataInicio == null || dataFim == null || dataInicio.isEqual(dataFim)) {
            return 0;
        }

        boolean invertido = dataFim.isBefore(dataInicio);
        LocalDate start = invertido ? dataFim : dataInicio;
        LocalDate end = invertido ? dataInicio : dataFim;

        int count = 0;
        LocalDate cursor = start.plusDays(1);
        while (!cursor.isAfter(end)) {
            if (isDiaUtil(cursor)) {
                count++;
            }
            cursor = cursor.plusDays(1);
        }

        return invertido ? -count : count;
    }

    @Override
    public LocalDate adicionarDiasUteis(LocalDate dataReferencia, int diasUteis) {
        Objects.requireNonNull(dataReferencia, "dataReferencia não pode ser nula");
        if (diasUteis == 0) {
            return dataReferencia;
        }

        int step = diasUteis > 0 ? 1 : -1;
        int remaining = Math.abs(diasUteis);
        LocalDate cursor = dataReferencia;

        while (remaining > 0) {
            cursor = cursor.plusDays(step);
            if (isDiaUtil(cursor)) {
                remaining--;
            }
        }

        return cursor;
    }

    /**
     * Feriados bancários nacionais adotados pela B3 para liquidação e negociação.
     */
    private boolean isFeriadoB3Padrao(LocalDate data) {
        int dia = data.getDayOfMonth();
        int mes = data.getMonthValue();
        int ano = data.getYear();

        // 1. Feriados Nacionais Fixos
        if (mes == 1 && dia == 1) return true;   // Confraternização Universal
        if (mes == 4 && dia == 21) return true;  // Tiradentes
        if (mes == 5 && dia == 1) return true;   // Dia do Trabalho
        if (mes == 9 && dia == 7) return true;   // Independência do Brasil
        if (mes == 10 && dia == 12) return true; // N. Sra. Aparecida
        if (mes == 11 && dia == 2) return true;  // Finados
        if (mes == 11 && dia == 15) return true; // Proclamação da República
        if (mes == 11 && dia == 20) return true; // Dia da Consciência Negra (Lei nº 14.759/2023)
        if (mes == 12 && dia == 25) return true; // Natal

        // 2. Feriados Móveis vinculados ao Domingo de Páscoa
        LocalDate pascoa = calcularDomingoDePascoa(ano);
        LocalDate carnavalSegunda = pascoa.minusDays(48);
        LocalDate carnavalTerca = pascoa.minusDays(47);
        LocalDate sextaFeiraSanta = pascoa.minusDays(2);
        LocalDate corpusChristi = pascoa.plusDays(60);

        return data.isEqual(carnavalSegunda)
            || data.isEqual(carnavalTerca)
            || data.isEqual(sextaFeiraSanta)
            || data.isEqual(corpusChristi);
    }

    private LocalDate calcularDomingoDePascoa(int ano) {
        int a = ano % 19;
        int b = ano / 100;
        int c = ano % 100;
        int d = b / 4;
        int e = b % 4;
        int f = (b + 8) / 25;
        int g = (b - f + 1) / 3;
        int h = (19 * a + b - d - g + 15) % 30;
        int i = c / 4;
        int k = c % 4;
        int l = (32 + 2 * e + 2 * i - h - k) % 7;
        int m = (a + 11 * h + 22 * l) / 451;
        int mes = (h + l - 7 * m + 114) / 31;
        int dia = ((h + l - 7 * m + 114) % 31) + 1;

        return LocalDate.of(ano, mes, dia);
    }
}
