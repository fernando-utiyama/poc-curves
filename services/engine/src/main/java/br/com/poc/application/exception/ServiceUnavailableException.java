package br.com.poc.application.exception;

import jakarta.annotation.Nullable;

import java.io.Serial;

import static br.com.poc.application.exception.InfraErrorCode.SERVICE_UNAVAILABLE;
import static java.util.Optional.ofNullable;

public class ServiceUnavailableException extends BaseException {

    @Serial
    private static final long serialVersionUID = 1L;

    public ServiceUnavailableException(String origin, @Nullable String method, @Nullable String message, @Nullable Throwable cause) {
        super(SERVICE_UNAVAILABLE.formatSubCode(origin, method),
            ofNullable(message).orElse(SERVICE_UNAVAILABLE.getMessage()),
            cause);
    }

    public ServiceUnavailableException(String code, String message) { super(code, message); }

    public ServiceUnavailableException(Throwable cause) { super(SERVICE_UNAVAILABLE, cause); }

    public ServiceUnavailableException() { super(SERVICE_UNAVAILABLE); }
}
