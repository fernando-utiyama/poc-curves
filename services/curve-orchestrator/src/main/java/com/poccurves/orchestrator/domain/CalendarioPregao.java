package com.poccurves.orchestrator.domain;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Calendário de pregão B3/ANBIMA: feriados nacionais fixos e móveis, e a
 * decisão de dia de pregão. Feriados móveis calculados pelo algoritmo de
 * Páscoa gregoriano (Meeus/Jones/Butcher) — verificados contra o calendário
 * oficial ANBIMA de feriados nacionais de 2026 (anbima.com.br/feriados).
 * Não inclui feriados municipais/estaduais — a B3 segue o calendário nacional.
 */
public final class CalendarioPregao {

    private static final int ANO_MINIMO_SUPORTADO = 2020;
    private static final int ANO_MAXIMO_SUPORTADO = 2035;

    private CalendarioPregao() {
        // Classe utilitária, sem instanciação
    }

    /**
     * Domingo de Páscoa de um ano, calculado pelo algoritmo de Meeus/Jones/Butcher.
     */
    private static LocalDate calcularDomingoPascoa(int ano) {
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

    /**
     * Feriados nacionais (fixos + móveis) de um ano.
     * Feriados móveis: Carnaval (segunda e terça, Páscoa -48 e -47 dias),
     * Sexta-feira Santa/Paixão de Cristo (Páscoa -2 dias), Corpus Christi
     * (Páscoa +60 dias). Quinta-feira Santa NÃO é feriado — Resolução ANBIMA
     * nº 2.516 (dia útil desde 2000).
     *
     * @param ano o ano para o qual calcular os feriados
     * @return conjunto de datas dos feriados nacionais daquele ano
     */
    public static Set<LocalDate> feriadosDoAno(int ano) {
        Set<LocalDate> feriados = new HashSet<>();

        // Feriados nacionais fixos, espelhando o calendário ANBIMA
        feriados.add(LocalDate.of(ano, 1, 1));   // Confraternização Universal
        feriados.add(LocalDate.of(ano, 4, 21));  // Tiradentes
        feriados.add(LocalDate.of(ano, 5, 1));   // Dia do Trabalho
        feriados.add(LocalDate.of(ano, 9, 7));   // Independência
        feriados.add(LocalDate.of(ano, 10, 12)); // Nossa Senhora Aparecida
        feriados.add(LocalDate.of(ano, 11, 2));  // Finados
        feriados.add(LocalDate.of(ano, 11, 15)); // Proclamação da República
        feriados.add(LocalDate.of(ano, 11, 20)); // Consciência Negra — feriado nacional desde 2024 (Lei 14.759/2023)
        feriados.add(LocalDate.of(ano, 12, 25)); // Natal

        // Feriados móveis relativos ao Domingo de Páscoa
        LocalDate domingoPascoa = calcularDomingoPascoa(ano);
        feriados.add(domingoPascoa.minusDays(48)); // Carnaval — segunda
        feriados.add(domingoPascoa.minusDays(47)); // Carnaval — terça
        feriados.add(domingoPascoa.minusDays(2));  // Sexta-feira Santa
        feriados.add(domingoPascoa.plusDays(60));  // Corpus Christi

        return Collections.unmodifiableSet(feriados);
    }

    /**
     * Decide se uma data é dia de pregão B3: não é sábado, não é
     * domingo, e não é feriado nacional. Datas fora do intervalo suportado
     * falham explicitamente — nunca assume que uma data fora do intervalo é
     * dia útil.
     *
     * @param data a data a ser verificada
     * @return true se for dia de pregão (dia útil B3), false caso contrário
     * @throws NullPointerException     se data for nula
     * @throws IllegalArgumentException se o ano da data estiver fora do intervalo [2020, 2035]
     */
    public static boolean ehDiaDePregao(LocalDate data) {
        Objects.requireNonNull(data, "data não pode ser nula");

        int ano = data.getYear();
        if (ano < ANO_MINIMO_SUPORTADO || ano > ANO_MAXIMO_SUPORTADO) {
            throw new IllegalArgumentException(
                    String.format("ano %d fora do intervalo suportado pelo calendário [%d, %d]",
                            ano, ANO_MINIMO_SUPORTADO, ANO_MAXIMO_SUPORTADO));
        }

        DayOfWeek diaDaSemana = data.getDayOfWeek();
        if (diaDaSemana == DayOfWeek.SATURDAY || diaDaSemana == DayOfWeek.SUNDAY) {
            return false;
        }

        return !feriadosDoAno(ano).contains(data);
    }

    /**
     * Conta quantos dias de pregão (dias úteis B3) existem no intervalo [dataInicio, dataFim),
     * ou seja, dataInicio inclusive e dataFim exclusive.
     *
     * @param dataInicio data inicial (inclusive)
     * @param dataFim data final (exclusive)
     * @return o número de dias de pregão no intervalo
     * @throws NullPointerException     se dataInicio ou dataFim forem nulas
     * @throws IllegalArgumentException se dataFim for anterior a dataInicio
     */
    public static long diasUteisEntre(LocalDate dataInicio, LocalDate dataFim) {
        Objects.requireNonNull(dataInicio, "dataInicio não pode ser nula");
        Objects.requireNonNull(dataFim, "dataFim não pode ser nula");
        if (dataFim.isBefore(dataInicio)) {
            throw new IllegalArgumentException(
                    "dataFim (" + dataFim + ") não pode ser anterior a dataInicio (" + dataInicio + ")");
        }

        long contagem = 0;
        LocalDate atual = dataInicio;
        while (atual.isBefore(dataFim)) {
            if (ehDiaDePregao(atual)) {
                contagem++;
            }
            atual = atual.plusDays(1);
        }
        return contagem;
    }
}
