package br.com.poc.adapter.in.api.rest.exception.handler;

import br.com.poc.application.exception.*;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;

import java.util.Map;
import java.util.function.Predicate;

import static java.util.Optional.ofNullable;

/**
 * Handler global de exceções especializado no mapeamento de exceções de domínio para respostas HTTP.
 *
 * <p>Intercepta exceções específicas do domínio da aplicação ({@link BaseException}) e as converte em respostas
 * HTTP padronizadas, conforme a RFC-9457 (Problem Details for HTTP APIs).</p>
 *
 * @see BaseException
 * @see AbstractRestExceptionHandler
 */
@ControllerAdvice
public class ApplicationExceptionHandler extends AbstractRestExceptionHandler {

    static final String TYPE_CODE_PREFIX = "poc.errors.type.";
    static final String TITLE_CODE_PREFIX = "poc.errors.title.";
    static final String DETAIL_CODE_PREFIX = "poc.errors.";

    static final String CODE_PROPERTY_NAME = "code";
    static final String MORE_INFOS_PROPERTY_NAME = "moreInfos";

    public ApplicationExceptionHandler() { super(); }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Object> handleException(BusinessException ex, WebRequest request) {
        return handleBaseException(ex, HttpStatus.UNPROCESSABLE_ENTITY, request);
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<Object> handleException(NotFoundException ex, WebRequest request) {
        ex.addMoreInfo("04", "item-nao-encontrado");
        return handleBaseException(ex, HttpStatus.NOT_FOUND, request);
    }

    @ExceptionHandler(InvalidInputException.class)
    public ResponseEntity<Object> handleException(InvalidInputException ex, WebRequest request) {
        return handleBaseException(ex, HttpStatus.BAD_REQUEST, request);
    }

    @ExceptionHandler(InfrastructureException.class)
    public ResponseEntity<Object> handleException(InfrastructureException ex, WebRequest request) {
        return handleBaseException(ex, HttpStatus.INTERNAL_SERVER_ERROR, request);
    }

    @ExceptionHandler(ServiceUnavailableException.class)
    public ResponseEntity<Object> handleException(ServiceUnavailableException ex, WebRequest request) {
        return handleBaseException(ex, HttpStatus.SERVICE_UNAVAILABLE, request);
    }

    /**
     * Tratamento base para exceções que estendem {@link BaseException}.
     *
     * <p>O erro será mapeado para um {@link ErrorResponse}, sendo tratado a padronização da resposta, assim como
     * a internacionalização das mensagens de erro.</p>
     *
     * @param ex      Exception a ser tratada
     * @param status  HTTP Status
     * @param request WebRequest
     * @return ResponseEntity com o corpo do erro formatado
     */
    protected ResponseEntity<Object> handleBaseException(
        @NonNull BaseException ex,
        @NonNull HttpStatusCode status,
        @NonNull WebRequest request) {

        final var builder = ErrorResponse.builder(ex, status, ex.getErrorMessage())
            .property(CODE_PROPERTY_NAME, ex.getErrorCode())
            .typeMessageCode(TYPE_CODE_PREFIX + ex.getErrorCode())
            .titleMessageCode(TITLE_CODE_PREFIX + ex.getErrorCode())
            .detailMessageCode(DETAIL_CODE_PREFIX + ex.getErrorCode());

        ofNullable(ex.getAdditionalDetails())
            .ifPresent(builder::detailMessageArguments);
        ofNullable(ex.getMoreInfos())
            .filter(Predicate.not(Map::isEmpty))
            .ifPresent((Map<String, String> moreInfos) -> builder.property(MORE_INFOS_PROPERTY_NAME, moreInfos));

        final var body = builder.build().updateAndGetBody(getMessageSource(), LocaleContextHolder.getLocale());

        return handleExceptionInternal(ex, body, new HttpHeaders(), status, request);
    }
}
