package br.com.poc.application.model.leiaute;

import br.com.poc.application.exception.CargaException;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.stream.Collectors;

/** Só a parte do texto (canonizar e data-base). O interpretar(...) por posições fica como no guia, seção 11.1. */
public final class LeiauteTaxaSwap {

    private LeiauteTaxaSwap() {}

    /**
     * Forma canônica: linhas por \r\n, \n ou \r; sem linhas vazias ou só com espaços; cada linha como está;
     * junção por \n, com \n no fim; Latin-1. O hash, o idCarga e o Blob usam estes bytes.
     */
    public static byte[] canonizar(byte[] texto) {
        String conteudo = new String(texto, StandardCharsets.ISO_8859_1);
        String canonico = Arrays.stream(conteudo.split("\r\n|\n|\r"))
            .filter(linha -> !linha.isBlank())
            .collect(Collectors.joining("\n", "", "\n"));
        if (canonico.equals("\n")) {
            throw CargaException.invalido("TaxaSwap.txt sem linhas");
        }
        return canonico.getBytes(StandardCharsets.ISO_8859_1);
    }

    /** Posições 12 a 19 da primeira linha (AAAAMMDD). */
    public static LocalDate dataBase(byte[] canonico) {
        String primeira = new String(canonico, StandardCharsets.ISO_8859_1).lines().findFirst().orElse("");
        if (primeira.length() < 19) {
            throw CargaException.invalido("Primeira linha curta demais para ter a data (posições 12 a 19)");
        }
        try {
            return LocalDate.parse(primeira.substring(11, 19), DateTimeFormatter.BASIC_ISO_DATE);
        } catch (DateTimeParseException e) {
            throw CargaException.invalido("Data inválida nas posições 12 a 19: " + primeira.substring(11, 19));
        }
    }
}
