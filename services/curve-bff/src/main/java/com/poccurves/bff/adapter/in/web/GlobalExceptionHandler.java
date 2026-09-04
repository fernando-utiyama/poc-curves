package com.poccurves.bff.adapter.in.web;

import com.poccurves.bff.dto.BffDtos.ErroResposta;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;

import java.util.List;
import java.util.NoSuchElementException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<ErroResposta> handleNotFound(NoSuchElementException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErroResposta("NAO_ENCONTRADO", ex.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErroResposta> handleBadRequest(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErroResposta("REQUISICAO_INVALIDA", ex.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErroResposta> handleConflict(IllegalStateException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErroResposta("ESTADO_INVALIDO", ex.getMessage()));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErroResposta> handleUnauthorized(AuthenticationException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ErroResposta("NAO_AUTORIZADO", "Autenticação OIDC obrigatória ou token inválido."));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErroResposta> handleForbidden(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ErroResposta("ACESSO_NEGADO", "Perfil insuficiente para executar esta ação."));
    }

    @ExceptionHandler(HttpClientErrorException.class)
    public ResponseEntity<ErroResposta> handleHttpClientError(HttpClientErrorException ex) {
        return ResponseEntity.status(ex.getStatusCode())
                .body(new ErroResposta("ERRO_SERVICO_ORIGEM", ex.getStatusText()));
    }

    @ExceptionHandler(HttpServerErrorException.class)
    public ResponseEntity<ErroResposta> handleHttpServerError(HttpServerErrorException ex) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(new ErroResposta("SERVICO_INDISPONIVEL", "Serviço interno respondeu com erro: " + ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErroResposta> handleValidation(MethodArgumentNotValidException ex) {
        List<String> erros = ex.getBindingResult().getFieldErrors().stream()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .toList();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErroResposta("ERRO_VALIDACAO", "Falha nos campos enviados.", erros));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErroResposta> handleGeneric(Exception ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErroResposta("ERRO_INTERNO", "Ocorreu um erro interno no BFF: " + ex.getMessage()));
    }
}
