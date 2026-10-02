package br.com.poc.application.exception;

import br.com.poc.adapter.in.api.rest.exception.handler.ApplicationExceptionHandler;

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
 * <p><b>Sempre que for criado uma categoria de erro, recomenda-se atualizar o handler de exceções
 * {@link ApplicationExceptionHandler} para mapear corretamente a nova exceção
 * para uma resposta HTTP adequada.</b></p>
 * <p>
 *
 * @implNote Não deve ser utilizado para mapear diretamente um código HTTP ou até herdar de
 * {@link org.springframework.web.ErrorResponseException}, os mapeamentos devem <b>sempre</b> estarem em
 * {@link ApplicationExceptionHandler}.
 * @see ApplicationExceptionHandler
 * @see BusinessException
 * @see InfrastructureException
 * @see ErrorCode
 */
public abstract class BaseException extends RuntimeException {

    private final String errorCode;
    private final String errorMessage;
    private final transient Object[] additionalDetails;
    private final Map<String, String> moreInfos;

    /**
     * @param errorCode         Código de erro personalizado
     * @param errorMessage      Mensagem padrão descritiva do erro
     * @param cause             Causa raiz da exceção
     * @param additionalDetails Detalhes adicionais sobre o erro
     */
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

    /**
     * @param errorCode         Código de erro personalizado
     * @param errorMessage      Mensagem padrão descritiva do erro
     * @param additionalDetails Argumentos para formatação da mensagem
     */
    protected BaseException(
        String errorCode,
        String errorMessage,
        Object[] additionalDetails
    ) {
        this(errorCode, errorMessage, null, additionalDetails);
    }

    /**
     * @param errorCode    Código de erro personalizado
     * @param errorMessage Mensagem padrão descritiva do erro
     * @param cause        Causa raiz da exceção
     */
    protected BaseException(
        String errorCode,
        String errorMessage,
        Throwable cause
    ) {
        this(errorCode, errorMessage, cause, null);
    }

    /**
     * @param errorCode    Código de erro personalizado
     * @param errorMessage Mensagem padrão descritiva do erro
     */
    protected BaseException(
        String errorCode,
        String errorMessage
    ) {
        this(errorCode, errorMessage, null, null);
    }

    /**
     * @param errorCode         Código de erro personalizado
     * @param additionalDetails Argumentos para formatação da mensagem
     */
    protected BaseException(
        ErrorCode errorCode,
        Object[] additionalDetails
    ) {
        this(errorCode.getCode(), errorCode.getMessage(), null, additionalDetails);
    }

    /**
     * @param errorCode Código de erro personalizado
     * @param cause     Causa raiz da exceção
     */
    protected BaseException(
        ErrorCode errorCode,
        Throwable cause
    ) {
        this(errorCode.getCode(), errorCode.getMessage(), cause, null);
    }

    /**
     * @param errorCode Código de erro personalizado
     */
    protected BaseException(
        ErrorCode errorCode
    ) {
        this(errorCode.getCode(), errorCode.getMessage(), null, null);
    }

    /**
     * Retorna o código de erro personalizado associado à exceção.
     *
     * @return Código de erro
     */
    public String getErrorCode() { return this.errorCode; }

    /**
     * Retorna a mensagem de erro descritiva associada à exceção.
     *
     * @return Mensagem de erro
     */
    public String getErrorMessage() { return this.errorMessage; }

    /**
     * Retorna detalhes adicionais sobre o erro para formatação da mensagem via i18n (arquivos externo).
     * <p>
     * PS: estes argumentos não serão utilizados para formatação da mensagem de erro, esta deverá vir já formatada.
     * </p>
     *
     * @return uma cópia defensiva do array de Detalhes adicionais
     */
    public Object[] getAdditionalDetails() {
        if (additionalDetails == null) {
            return null;
        }
        return Arrays.copyOf(additionalDetails, additionalDetails.length);
    }

    /**
     * Retorna um mapa de informações adicionais associadas à exceção.
     *
     * @return Mapa de informações adicionais
     */
    public Map<String, String> getMoreInfos() { return this.moreInfos; }

    /**
     * Adiciona uma informação adicional ao mapa de mais informações.
     *
     * @param code    Código da informação adicional
     * @param message Mensagem da informação adicional
     */
    public void addMoreInfo(String code, String message) { this.moreInfos.put(code, message); }
}
