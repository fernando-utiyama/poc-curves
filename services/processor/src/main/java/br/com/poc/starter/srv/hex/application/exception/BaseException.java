package br.com.poc.starter.srv.hex.application.exception;

import br.com.poc.starter.srv.hex.util.ErrorCode;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * <p>Exceção personalizada para tratar os erros conhecidos da aplicação, adicionando um código e uma mensagem atrelada,
 * além de detalhes adicionais e informações extras conforme necessário.</p>
 *
 * <p>Este erro deve ser utilizado para representar falhas que ocorrem dentro da lógica da aplicação,
 * como violações de regras de negócio, falhas de validação, problemas de integração com serviços externos,
 * ou qualquer outra situação que exija um tratamento específico.</p>
 *
 * @see BusinessException
 * @see ErrorCode
 */
public abstract class BaseException extends RuntimeException {

    private final String errorCode;
    private final String errorMessage;
    private final transient Object[] additionalDetails;
    private final Map<String, String> moreInfos;

    protected BaseException(
        String errorCode,
        String errorMessage,
        Throwable cause,
        Object[] additionalDetails
    ) {
        super(errorMessage, cause);
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        this.additionalDetails = additionalDetails == null
            ? null
            : Arrays.copyOf(additionalDetails, additionalDetails.length);
        this.moreInfos = new HashMap<>();
    }

    protected BaseException(
        String errorCode,
        String errorMessage,
        Object[] additionalDetails
    ) {
        this(errorCode, errorMessage, null, additionalDetails);
    }

    protected BaseException(
        String errorCode,
        String errorMessage,
        Throwable cause
    ) {
        this(errorCode, errorMessage, cause, null);
    }

    protected BaseException(
        String errorCode,
        String errorMessage
    ) {
        this(errorCode, errorMessage, null, null);
    }

    protected BaseException(
        ErrorCode errorCode,
        Object[] additionalDetails
    ) {
        this(errorCode.getCode(), errorCode.getMessage(), null, additionalDetails);
    }

    protected BaseException(
        ErrorCode errorCode,
        Throwable cause
    ) {
        this(errorCode.getCode(), errorCode.getMessage(), cause, null);
    }

    protected BaseException(
        ErrorCode errorCode
    ) {
        this(errorCode.getCode(), errorCode.getMessage(), null, null);
    }

    public String getErrorCode() { return this.errorCode; }

    public String getErrorMessage() { return this.errorMessage; }

    public Object[] getAdditionalDetails() {
        if (additionalDetails == null) {
            return null;
        }
        return Arrays.copyOf(additionalDetails, additionalDetails.length);
    }

    public Map<String, String> getMoreInfos() { return this.moreInfos; }

    public void addMoreInfo(String code, String message) { this.moreInfos.put(code, message); }
}
