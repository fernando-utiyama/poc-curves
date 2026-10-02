package br.com.poc.application.exception;

import jakarta.annotation.Nullable;

import static br.com.poc.application.exception.InfraErrorCode.BAD_REQUEST;
import static java.util.Optional.ofNullable;

/**
 * Exceção especializada para indicar que um recurso solicitado não foi encontrado no sistema.
 *
 * @see BaseException
 */
public class InvalidInputException extends BaseException {

    public InvalidInputException(String origin, @Nullable String method, @Nullable String message, @Nullable Throwable cause) {
        super(BAD_REQUEST.formatSubCode(origin, method), ofNullable(message).orElse(BAD_REQUEST.getMessage()), cause);
    }

    public InvalidInputException(String origin, @Nullable String method, @Nullable String message) {
        super(BAD_REQUEST.formatSubCode(origin, method), ofNullable(message).orElse(BAD_REQUEST.getMessage()));
    }

    public InvalidInputException(Throwable cause) { super(BAD_REQUEST, cause); }

    public InvalidInputException() { super(BAD_REQUEST); }
}
