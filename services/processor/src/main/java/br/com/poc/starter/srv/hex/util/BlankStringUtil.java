package br.com.poc.starter.srv.hex.util;

import java.util.function.Supplier;

import static java.util.Objects.isNull;
import static java.util.Objects.requireNonNull;

/**
 * Classe utilitária para operações com Strings em branco.
 *
 * @author Chapter ENG.SW Java | CORE Backend
 * @since 1.0.0
 */
public final class BlankStringUtil {

    private BlankStringUtil() { throw new IllegalStateException("Utility class"); }

    /**
     * Verifica se a String é nula ou vazia (considerando espaços em branco).
     *
     * @param str String a ser verificada
     * @return true se a String for nula ou vazia, false caso contrário
     * @see String#isBlank()
     * @see #nonBlank(String)
     */
    public static boolean isBlank(String str) { return (str == null || str.isBlank()); }

    /**
     * Verifica se a String não é nula e não está vazia (considerando espaços em branco).
     *
     * @param str String a ser verificada
     * @return true se a String não for nula e não estiver vazia, false caso contrário
     * @see String#isBlank()
     */
    public static boolean nonBlank(String str) { return !isBlank(str); }

    /**
     * Verifica se a String não é nula e não está vazia (considerando espaços em branco).
     * Caso a String seja nula ou vazia, lança IllegalArgumentException com a mensagem
     * fornecida pelo Supplier.
     *
     * @param str             String a ser verificada
     * @param messageSupplier Supplier que fornece a mensagem da exceção
     * @return a String original se não for nula ou vazia
     */
    public static String requireNonBlank(String str, Supplier<String> messageSupplier) {
        if (isBlank(str)) {
            throw new IllegalArgumentException(isNull(messageSupplier) ? null : messageSupplier.get());
        }
        return str;
    }

    /**
     * Verifica se a String não é nula e não está vazia (considerando espaços em branco).
     * Caso a String seja nula ou vazia, lança IllegalArgumentException com a mensagem
     * fornecida.
     *
     * @param str     String a ser verificada
     * @param message Mensagem da exceção
     * @return a String original se não for nula ou vazia
     */
    public static String requireNonBlank(String str, String message) { return requireNonBlank(str, () -> message); }

    /**
     * Verifica se a String não é nula e não está vazia (considerando espaços em branco).
     *
     * @param str String a ser verificada
     * @return a String original se não for nula ou vazia
     */
    public static String requireNonBlank(String str) { return requireNonBlank(str, () -> null); }

    /**
     * Retorna a String original se não for nula ou vazia (considerando espaços em branco).
     * Caso contrário, retorna a String padrão fornecida, que também deve não ser nula ou vazia.
     * Caso a String padrão seja nula ou vazia, lança IllegalArgumentException.
     *
     * @param str        String a ser verificada
     * @param defaultStr String padrão a ser retornada se a original for nula ou vazia
     * @return a String original ou a String padrão
     */
    public static String requireNonBlankElse(String str, String defaultStr) {
        return nonBlank(str) ? str : requireNonBlank(defaultStr, "defaultStr");
    }

    /**
     * Retorna a String original se não for nula ou vazia (considerando espaços em branco).
     * Caso contrário, obtém a String padrão através do Supplier fornecido, que também deve não ser nula ou vazia.
     * Caso a String padrão seja nula ou vazia, lança IllegalArgumentException.
     *
     * @param str                 String a ser verificada
     * @param defaultStrSupplier  Supplier que fornece a String padrão a ser retornada se a original for nula ou vazia
     * @return a String original ou a String padrão
     */
    public static String requireNonBlankElseGet(String str, Supplier<String> defaultStrSupplier) {
        return nonBlank(str) ? str
            : requireNonBlank(requireNonNull(defaultStrSupplier, "defaultStrSupplier").get(), "defaultStrSupplier.get()");
    }
}
