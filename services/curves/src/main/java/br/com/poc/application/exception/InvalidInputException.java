package br.com.poc.application.exception;

import java.io.Serial;

public class InvalidInputException extends BaseException {

    @Serial
    private static final long serialVersionUID = 1L;

    public InvalidInputException(String errorCode, String errorMessage, Throwable cause, Object[] additionalDetails) {
        super(errorCode, errorMessage, cause, additionalDetails);
    }

    public InvalidInputException(String errorCode, String errorMessage, Object[] additionalDetails) {
        super(errorCode, errorMessage, additionalDetails);
    }

    public InvalidInputException(String errorCode, String errorMessage, Throwable cause) {
        super(errorCode, errorMessage, cause);
    }

    public InvalidInputException(String errorCode, String errorMessage) { super(errorCode, errorMessage); }

    public InvalidInputException(ErrorCode errorCode, Object[] additionalDetails) { super(errorCode, additionalDetails); }

    public InvalidInputException(ErrorCode errorCode, Throwable cause) { super(errorCode, cause); }

    public InvalidInputException(ErrorCode errorCode) { super(errorCode); }
}
