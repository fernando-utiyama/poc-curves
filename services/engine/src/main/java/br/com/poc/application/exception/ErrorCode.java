package br.com.poc.application.exception;

import org.springframework.lang.Nullable;

import static java.util.Objects.nonNull;

/**
 * Interface que define o contrato para códigos de erro utilizados na aplicação.
 * Cada código de erro deve possuir um identificador único e uma mensagem descritiva.
 */
public interface ErrorCode {

    /**
     * Retorna o código único identificador do erro.
     *
     * @return o código único do erro (ex: "ERRO0001")
     */
    String getCode();

    /**
     * Retorna a mensagem descritiva do erro.
     *
     * @return a mensagem descritiva técnica do erro
     */
    String getMessage();

    /**
     * Formata o código de erro incluindo o nome da classe e opcionalmente o método.
     *
     * @param clazz      a classe onde o erro ocorreu
     * @param methodName o método onde o erro ocorreu (pode ser nulo)
     * @param <T>        o tipo da classe
     * @return o código de erro formatado (ex: "MinhaClasse.meuMetodo.ERRO0001" ou "MinhaClasse.ERRO0001")
     */
    default <T> String formatSubCode(Class<T> clazz, @Nullable String methodName) {
        return formatSubCode(clazz.getSimpleName(), methodName);
    }

    /**
     * Formata o código de erro incluindo o nome da classe e opcionalmente o método.
     *
     * @param className  o nome da classe onde o erro ocorreu
     * @param methodName o método onde o erro ocorreu (pode ser nulo)
     * @return o código de erro formatado (ex: "MinhaClasse.meuMetodo.ERRO0001" ou "MinhaClasse.ERRO0001")
     */
    default String formatSubCode(String className, @Nullable String methodName) {
        if (nonNull(methodName)) {
            return String.format("%s.%s.%s", className, methodName, this.getCode());
        }
        return String.format("%s.%s", className, this.getCode());
    }
}
