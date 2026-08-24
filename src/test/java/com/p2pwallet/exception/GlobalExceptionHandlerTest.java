package com.p2pwallet.exception;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    @Test
    @DisplayName("should handle InsufficientFundsException with 422")
    void shouldHandleInsufficientFunds() {
        InsufficientFundsException ex = new InsufficientFundsException("Not enough balance");

        ResponseEntity<Map<String, Object>> response = handler.handleInsufficientFunds(ex);

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, response.getStatusCode());
        assertEquals(422, response.getBody().get("status"));
        assertEquals("INSUFFICIENT_FUNDS", response.getBody().get("error"));
        assertEquals("Not enough balance", response.getBody().get("message"));
        assertNotNull(response.getBody().get("timestamp"));
    }

    @Test
    @DisplayName("should handle IdempotencyConflictException with 409")
    void shouldHandleIdempotencyConflict() {
        IdempotencyConflictException ex = new IdempotencyConflictException("Duplicate key");

        ResponseEntity<Map<String, Object>> response = handler.handleIdempotencyConflict(ex);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals(409, response.getBody().get("status"));
        assertEquals("IDEMPOTENCY_CONFLICT", response.getBody().get("error"));
        assertEquals("Duplicate key", response.getBody().get("message"));
    }

    @Test
    @DisplayName("should handle IllegalArgumentException with 400")
    void shouldHandleBadRequest() {
        IllegalArgumentException ex = new IllegalArgumentException("Invalid input");

        ResponseEntity<Map<String, Object>> response = handler.handleBadRequest(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(400, response.getBody().get("status"));
        assertEquals("BAD_REQUEST", response.getBody().get("error"));
        assertEquals("Invalid input", response.getBody().get("message"));
    }

    @Test
    @DisplayName("should handle MethodArgumentNotValidException with 400")
    void shouldHandleValidation() {
        BindingResult bindingResult = mock(BindingResult.class);
        FieldError fieldError = new FieldError("request", "userId", "must not be blank");
        when(bindingResult.getFieldErrors()).thenReturn(List.of(fieldError));

        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        when(ex.getBindingResult()).thenReturn(bindingResult);

        ResponseEntity<Map<String, Object>> response = handler.handleValidation(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(400, response.getBody().get("status"));
        assertEquals("VALIDATION_ERROR", response.getBody().get("error"));
        assertTrue(((String) response.getBody().get("message")).contains("userId"));
    }

    @Test
    @DisplayName("should handle multiple validation errors")
    void shouldHandleMultipleValidationErrors() {
        BindingResult bindingResult = mock(BindingResult.class);
        FieldError error1 = new FieldError("request", "from", "must not be null");
        FieldError error2 = new FieldError("request", "amount", "must be positive");
        when(bindingResult.getFieldErrors()).thenReturn(List.of(error1, error2));

        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        when(ex.getBindingResult()).thenReturn(bindingResult);

        ResponseEntity<Map<String, Object>> response = handler.handleValidation(ex);

        String message = (String) response.getBody().get("message");
        assertTrue(message.contains("from"));
        assertTrue(message.contains("amount"));
    }

    @Test
    @DisplayName("should handle generic Exception with 500")
    void shouldHandleGenericException() {
        Exception ex = new RuntimeException("Something went wrong");

        ResponseEntity<Map<String, Object>> response = handler.handleGeneric(ex);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals(500, response.getBody().get("status"));
        assertEquals("INTERNAL_ERROR", response.getBody().get("error"));
        assertEquals("An unexpected error occurred", response.getBody().get("message"));
    }
}
