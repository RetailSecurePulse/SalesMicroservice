package com.retailpulse.exception;

import com.retailpulse.controller.ErrorResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler globalExceptionHandler = new GlobalExceptionHandler();

    @Test
    void handleIllegalArgument_returnsBadRequestWithMessage() {
        ResponseEntity<String> response = globalExceptionHandler.handleIllegalArgument(
                new IllegalArgumentException("invalid quantity")
        );

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("invalid quantity", response.getBody());
    }

    @Test
    void handleRuntimeException_returnsBadRequestWithMessage() {
        ResponseEntity<String> response = globalExceptionHandler.handleRuntimeException(
                new RuntimeException("runtime failure")
        );

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("runtime failure", response.getBody());
    }

    @Test
    void applicationExceptionHandler_returnsStructuredErrorResponse() {
        ResponseEntity<ErrorResponse> response = globalExceptionHandler.applicationExceptionHandler(
                new ApplicationException("APP_001", "application failure")
        );

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertInstanceOf(ErrorResponse.class, response.getBody());
        assertEquals("APP_001", response.getBody().getCode());
        assertEquals("application failure", response.getBody().getMessage());
    }

    @Test
    void handleBusinessException_returnsStructuredErrorResponse() {
        ResponseEntity<ErrorResponse> response = globalExceptionHandler.handleBusinessException(
                new BusinessException("BUS_001", "business failure")
        );

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertInstanceOf(ErrorResponse.class, response.getBody());
        assertEquals("BUS_001", response.getBody().getCode());
        assertEquals("business failure", response.getBody().getMessage());
    }
}
