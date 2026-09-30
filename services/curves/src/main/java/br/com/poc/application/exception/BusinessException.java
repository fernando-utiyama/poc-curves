package br.com.poc.application.exception;

import java.io.Serial;

public class BusinessException extends BaseException {

    @Serial
    private static final long serialVersionUID = 1L;

    public BusinessException(String errorCode, String errorMessage, Throwable cause, Object[] additionalDetails) {
        super(errorCode, errorMessage, cause, additionalDetails);
    }

    public BusinessException(String errorCode, String errorMessage, Object[] additionalDetails) {
        super(errorCode, errorMessage, additionalDetails);
    }

    public BusinessException(String errorCode, String errorMessage, Throwable cause) {
        super(errorCode, errorMessage, cause);
    }

    public BusinessException(String errorCode, String errorMessage) { super(errorCode, errorMessage); }

    public BusinessException(ErrorCode errorCode, Object[] additionalDetails) { super(errorCode, additionalDetails); }

    public BusinessException(ErrorCode errorCode, Throwable cause) { super(errorCode, cause); }

    public BusinessException(ErrorCode errorCode) { super(errorCode); }
}
