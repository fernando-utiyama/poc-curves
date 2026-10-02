package br.com.poc.util;

/**
 * Classe utilitária para operações com Strings em branco.
 */
public final class BlankStringUtil {

    private BlankStringUtil() { throw new IllegalStateException("Utility class"); }

    /**
     * Verifica se a String é nula ou vazia (considerando espaços em branco).
     *
     * @param str String a ser verificada
     * @return true se a String for nula ou vazia, false caso contrário
     */
    public static boolean isBlank(String str) { return str == null || str.isBlank(); }

    /**
     * Verifica se a String não é nula e não está vazia (considerando espaços em branco).
     *
     * @param str String a ser verificada
     * @return true se a String não for nula e não estiver vazia, false caso contrário
     */
    public static boolean nonBlank(String str) { return !isBlank(str); }
}
