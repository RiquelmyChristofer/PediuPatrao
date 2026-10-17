package com.umc.pediupatrao.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MissingServletRequestParameterException;

class ApiExceptionHandlerTest {

    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @Test
    void returnsFriendlyMessageForInvalidRequest() {
        var response = handler.handleInvalidRequest(
                new MissingServletRequestParameterException("status", "String"));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(Map.of("erro", "Confira os dados informados e tente novamente."), response.getBody());
    }

    @Test
    void returnsFriendlyMessageForForbiddenAction() {
        var response = handler.handleAccessDenied(new AccessDeniedException("Ação não permitida."));

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertEquals(Map.of("erro", "Ação não permitida."), response.getBody());
    }
}
