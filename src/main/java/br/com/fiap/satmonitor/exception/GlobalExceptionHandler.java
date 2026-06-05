package br.com.fiap.satmonitor.exception;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ErroResponse> handleEntityNotFound(EntityNotFoundException ex, HttpServletRequest req) {
        log.warn(ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(buildErro(404, ex.getMessage(), req));
    }

    @ExceptionHandler(AcessoNegadoException.class)
    public ResponseEntity<ErroResponse> handleAcessoNegado(AcessoNegadoException ex, HttpServletRequest req) {
        log.warn(ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(buildErro(403, ex.getMessage(), req));
    }

    @ExceptionHandler(SenhaMissaoInvalidaException.class)
    public ResponseEntity<ErroResponse> handleSenhaMissaoInvalida(SenhaMissaoInvalidaException ex, HttpServletRequest req) {
        log.warn(ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(buildErro(401, ex.getMessage(), req));
    }

    @ExceptionHandler(OperadorJaMembroException.class)
    public ResponseEntity<ErroResponse> handleOperadorJaMembro(OperadorJaMembroException ex, HttpServletRequest req) {
        log.warn(ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(buildErro(409, ex.getMessage(), req));
    }

    @ExceptionHandler(DonoUnicoException.class)
    public ResponseEntity<ErroResponse> handleDonoUnico(DonoUnicoException ex, HttpServletRequest req) {
        log.warn(ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(buildErro(400, ex.getMessage(), req));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErroResponse> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest req) {
        String erros = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .collect(Collectors.joining("; "));
        log.warn("Erros de validação: {}", erros);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(buildErro(400, erros, req));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErroResponse> handleNotReadable(HttpMessageNotReadableException ex, HttpServletRequest req) {
        log.warn("Corpo da requisição inválido: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(buildErro(400, "Corpo da requisição inválido ou mal formatado", req));
    }

    @ExceptionHandler(TooManyRequestsException.class)
    public ResponseEntity<ErroResponse> handleTooManyRequests(TooManyRequestsException ex, HttpServletRequest req) {
        log.warn("Rate limit atingido: {}", ex.getMessage());
        return ResponseEntity.status(429).body(buildErro(429, ex.getMessage(), req));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErroResponse> handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest req) {
        log.warn(ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(buildErro(400, ex.getMessage(), req));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErroResponse> handleAccessDenied(AccessDeniedException ex, HttpServletRequest req) {
        log.warn(ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(buildErro(403, "Acesso negado", req));
    }

    // Falha de login (usuário inexistente ou senha incorreta) → mesma resposta, sem enumeração
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErroResponse> handleAuthentication(AuthenticationException ex, HttpServletRequest req) {
        log.warn("Falha de autenticação: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(buildErro(401, "Credenciais inválidas", req));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErroResponse> handleGeneric(Exception ex, HttpServletRequest req) {
        log.error("Erro interno: ", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(buildErro(500, "Erro interno no servidor", req));
    }

    private ErroResponse buildErro(int status, String error, HttpServletRequest req) {
        return new ErroResponse(LocalDateTime.now(), status, error, req.getRequestURI());
    }
}
