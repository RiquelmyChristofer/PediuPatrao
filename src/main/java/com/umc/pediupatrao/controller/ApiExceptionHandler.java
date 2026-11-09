package com.umc.pediupatrao.controller;

import java.util.Map;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgument(IllegalArgumentException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("erro", mensagem(exception, "Confira os dados informados e tente novamente.")));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, String>> handleAccessDenied(AccessDeniedException exception) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("erro", mensagem(exception, "Você não tem permissão para realizar esta ação.")));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MissingServletRequestParameterException.class})
    public ResponseEntity<Map<String, String>> handleInvalidRequest(Exception exception) {
        return ResponseEntity.badRequest()
                .body(Map.of("erro", "Confira os dados informados e tente novamente."));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, String>> handleTypeMismatch(MethodArgumentTypeMismatchException exception) {
        return ResponseEntity.badRequest()
                .body(Map.of("erro", "Parâmetro '" + exception.getName()
                        + "' inválido. Datas devem seguir o formato AAAA-MM-DD e números devem ser inteiros."));
    }

    private String mensagem(Exception exception, String padrao) {
        String mensagem = exception.getMessage();
        return mensagem == null || mensagem.isBlank() ? padrao : mensagem;
    }
}
