package br.com.poc.application.exception;

/**
 * {@link ErrorCode} avulso, para códigos que não pertencem a nenhuma das enumerações.
 */
public record SimpleErrorCode(String code, String message) implements ErrorCode {

    @Override
    public String getCode() { return code; }

    @Override
    public String getMessage() { return message; }
}
