package br.com.poc.application.exception;

/**
 * Códigos de erro de negócio do serviço de curvas.
 *
 * @see BusinessException
 */
public enum BusinessErrorCode implements ErrorCode {

    CURVA_MERCD_NOT_FOUND("Curva de mercado não encontrada");

    private final String code;
    private final String message;

    BusinessErrorCode(final String message) {
        this.code = this.name();
        this.message = message;
    }

    @Override
    public String getCode() { return code; }

    @Override
    public String getMessage() { return message; }
}
