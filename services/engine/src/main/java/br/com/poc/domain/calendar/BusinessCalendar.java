package br.com.poc.domain.calendar;

import java.time.LocalDate;

/**
 * Porta de domínio para consulta de dias úteis e calendário de feriados (ex: ANBIMA, B3, Feriados Nacionais).
 *
 * <p>Conforme a Arquitetura Hexagonal, esta interface reside no núcleo de domínio,
 * desacoplada de tabelas de banco de dados ou provedores externos.</p>
 */
public interface BusinessCalendar {

    /**
     * Identificador do calendário (ex: "ANBIMA", "B3", "NACIONAL", "CORRIDO").
     */
    String getCalendarName();

    /**
     * Verifica se a data é um dia útil segundo as regras e feriados do calendário.
     */
    boolean isDiaUtil(LocalDate data);

    /**
     * Conta a quantidade de dias úteis no intervalo (dataInicio, dataFim].
     * Se dataInicio for igual a dataFim, retorna 0.
     * Se dataFim for anterior a dataInicio, retorna valor negativo.
     *
     * @param dataInicio Data de início (exclusiva, convenção D+0)
     * @param dataFim Data de fim (inclusiva, vencimento do vértice)
     * @return Quantidade de dias úteis no período
     */
    int contarDiasUteis(LocalDate dataInicio, LocalDate dataFim);

    /**
     * Adiciona ou subtrai dias úteis a partir de uma data de referência.
     *
     * @param dataReferencia Data base
     * @param diasUteis Quantidade de dias úteis a somar (ou subtrair se negativo)
     * @return Data futura/passada correspondente ao enésimo dia útil
     */
    LocalDate adicionarDiasUteis(LocalDate dataReferencia, int diasUteis);
}
