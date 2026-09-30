package br.com.poc.adapter.in.api.rest.handler;

import br.com.poc.shared.api.ApiErrorResponse;
import br.com.poc.application.exception.BusinessException;
import br.com.poc.application.exception.ExcecaoNegocio;
import br.com.poc.application.exception.NotFoundException;
import br.com.poc.application.exception.ProvedorNaoEncontradoException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.OffsetDateTime;
import java.util.List;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(ProvedorNaoEncontradoException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(ProvedorNaoEncontradoException ex,
                                                           HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiErrorResponse(
                        OffsetDateTime.now(),
                        HttpStatus.NOT_FOUND.value(),
                        "NOT_FOUND",
                        ex.getMessage(),
                        request.getRequestURI(),
                        List.of()
                ));
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(NotFoundException ex,
                                                           HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiErrorResponse(
                        OffsetDateTime.now(),
                        HttpStatus.NOT_FOUND.value(),
                        ex.getErrorCode(),
                        ex.getErrorMessage(),
                        request.getRequestURI(),
                        List.of()
                ));
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiErrorResponse> handleBusinessException(BusinessException ex,
                                                                    HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(new ApiErrorResponse(
                        OffsetDateTime.now(),
                        HttpStatus.UNPROCESSABLE_ENTITY.value(),
                        ex.getErrorCode(),
                        ex.getErrorMessage(),
                        request.getRequestURI(),
                        List.of()
                ));
    }

    @ExceptionHandler(ExcecaoNegocio.class)
    public ResponseEntity<ApiErrorResponse> handleBusiness(ExcecaoNegocio ex,
                                                           HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(new ApiErrorResponse(
                        OffsetDateTime.now(),
                        HttpStatus.UNPROCESSABLE_ENTITY.value(),
                        "BUSINESS_ERROR",
                        ex.getMessage(),
                        request.getRequestURI(),
                        List.of()
                ));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException ex,
                                                             HttpServletRequest request) {
        List<String> details = ex.getBindingResult()
                .getAllErrors()
                .stream()
                .map(error -> {
                    if (error instanceof FieldError fieldError) {
                        return fieldError.getField() + ": " + fieldError.getDefaultMessage();
                    }
                    return error.getDefaultMessage();
                })
                .toList();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiErrorResponse(
                        OffsetDateTime.now(),
                        HttpStatus.BAD_REQUEST.value(),
                        "VALIDATION_ERROR",
                        "Dados de entrada inválidos",
                        request.getRequestURI(),
                        details
                ));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleConstraintViolation(ConstraintViolationException ex,
                                                                      HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiErrorResponse(
                        OffsetDateTime.now(),
                        HttpStatus.BAD_REQUEST.value(),
                        "VALIDATION_ERROR",
                        "Violação de restrição",
                        request.getRequestURI(),
                        ex.getConstraintViolations().stream().map(v -> v.getMessage()).toList()
                ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleGeneric(Exception ex,
                                                          HttpServletRequest request) {
        log.error("Erro interno não tratado. path={}", request.getRequestURI(), ex);

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiErrorResponse(
                        OffsetDateTime.now(),
                        HttpStatus.INTERNAL_SERVER_ERROR.value(),
                        "INTERNAL_SERVER_ERROR",
                        "Ocorreu um erro interno inesperado",
                        request.getRequestURI(),
                        List.of()
                ));
    }
}
