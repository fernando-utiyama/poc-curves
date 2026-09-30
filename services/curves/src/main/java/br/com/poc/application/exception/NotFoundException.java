package br.com.poc.application.exception;

import jakarta.annotation.Nullable;

import java.io.Serial;

import static br.com.poc.application.exception.InfraErrorCode.NOT_FOUND;
import static java.util.Optional.ofNullable;

/**
 * Exceção especializada para indicar que um recurso solicitado não foi encontrado no sistema.
 *
 * @see BusinessException
 */
public class NotFoundException extends BaseException {

    @Serial
    private static final long serialVersionUID = 1L;

    public NotFoundException(String origin, @Nullable String method, @Nullable String message, @Nullable Throwable cause) {
        super(NOT_FOUND.formatSubCode(origin, method),
            ofNullable(message).orElse(NOT_FOUND.getMessage()),
            cause);
    }

    public NotFoundException(String code, String message) { super(code, message); }

    public NotFoundException(Throwable cause) { super(NOT_FOUND, cause); }

    public NotFoundException() { super(NOT_FOUND); }
}
