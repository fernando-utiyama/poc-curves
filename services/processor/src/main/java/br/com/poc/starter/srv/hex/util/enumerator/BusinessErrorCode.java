package br.com.poc.starter.srv.hex.util.enumerator;

import br.com.poc.starter.srv.hex.util.ErrorCode;

public enum BusinessErrorCode implements ErrorCode {

    DEPOSIT_CANNOT_EDIT("Depósito não pode ser editado no status atual"),
    DEPOSIT_CANNOT_CHANGE_STATUS("Não é possível alterar o status do depósito"),
    DEPOSIT_NOT_FOUND("Depósito não encontrado"),

    WITHDRAWAL_CANNOT_EDIT("Saque não pode ser editado no status atual"),
    WITHDRAWAL_CANNOT_CHANGE_STATUS("Não é possível alterar o status do saque"),
    WITHDRAWAL_NOT_FOUND("Saque não encontrado");

    private final String code;
    private final String message;

    BusinessErrorCode(String message) {
        this.code = name();
        this.message = message;
    }

    @Override
    public String getCode() { return code; }

    @Override
    public String getMessage() { return message; }
}
