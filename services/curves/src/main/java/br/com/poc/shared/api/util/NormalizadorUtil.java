package br.com.poc.shared.api.util;

public final class NormalizadorUtil {

    private NormalizadorUtil() {
    }

    public static String normalizarTextoObrigatorio(String valor) {
        if (valor == null) {
            throw new IllegalArgumentException("Valor obrigatório não pode ser nulo.");
        }

        String normalizado = valor.trim();

        if (normalizado.isEmpty()) {
            throw new IllegalArgumentException("Valor obrigatório não pode ser vazio.");
        }

        return normalizado;
    }

    public static String normalizarTextoOpcional(String valor) {
        if (valor == null) {
            return null;
        }

        String normalizado = valor.trim();
        return normalizado.isEmpty() ? null : normalizado;
    }

    public static boolean estaVazio(String valor) {
        return valor == null || valor.trim().isEmpty();
    }
}
