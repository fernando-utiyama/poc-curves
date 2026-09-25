package br.com.poc.application.exception;

import java.io.Serial;

public class InfrastructureException extends BaseException {

    @Serial
    private static final long serialVersionUID = 1L;

    public InfrastructureException(String errorCode, String errorMessage, Throwable cause, Object[] additionalDetails) {
        super(errorCode, errorMessage, cause, additionalDetails);
    }

    public InfrastructureException(String errorCode, String errorMessage, Object[] additionalDetails) {
        super(errorCode, errorMessage, additionalDetails);
    }

    public InfrastructureException(String errorCode, String errorMessage, Throwable cause) {
        super(errorCode, errorMessage, cause);
    }

    public InfrastructureException(String errorCode, String errorMessage) { super(errorCode, errorMessage); }

    public InfrastructureException(ErrorCode errorCode, Object[] additionalDetails) { super(errorCode, additionalDetails); }

    public InfrastructureException(ErrorCode errorCode, Throwable cause) { super(errorCode, cause); }

    public InfrastructureException(ErrorCode errorCode) { super(errorCode); }
}
