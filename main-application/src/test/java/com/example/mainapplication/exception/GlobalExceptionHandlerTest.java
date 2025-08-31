package com.example.mainapplication.exception;

import com.example.mainapplication.dto.ErrorResponse;
import org.axonframework.commandhandling.CommandExecutionException;
import org.axonframework.queryhandling.QueryExecutionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.KafkaException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.context.request.WebRequest;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler globalExceptionHandler;

    @Mock
    private WebRequest webRequest;

    @Mock
    private BindingResult bindingResult;

    @BeforeEach
    void setUp() {
        globalExceptionHandler = new GlobalExceptionHandler();
        when(webRequest.getDescription(false)).thenReturn("uri=/test");
        MDC.put("correlationId", "test-correlation-id");
    }

    @Test
    void handleValidationExceptions_ShouldReturnBadRequest() {
        // Given
        FieldError fieldError = new FieldError("testObject", "testField", "Test error message");
        when(bindingResult.getAllErrors()).thenReturn(Collections.singletonList(fieldError));
        
        MethodArgumentNotValidException exception = new MethodArgumentNotValidException(null, bindingResult);

        // When
        ResponseEntity<ErrorResponse> response = globalExceptionHandler.handleValidationExceptions(exception, webRequest);

        // Then
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Validation Failed", response.getBody().getError());
        assertEquals("test-correlation-id", response.getBody().getCorrelationId());
        assertTrue(response.getBody().getDetails().containsKey("testField"));
    }

    @Test
    void handleConstraintViolationException_ShouldReturnBadRequest() {
        // Given
        ConstraintViolation<?> violation = mock(ConstraintViolation.class);
        when(violation.getPropertyPath()).thenReturn(mock(jakarta.validation.Path.class));
        when(violation.getPropertyPath().toString()).thenReturn("testProperty");
        when(violation.getMessage()).thenReturn("Test constraint violation");
        
        Set<ConstraintViolation<?>> violations = Collections.singleton(violation);
        ConstraintViolationException exception = new ConstraintViolationException(violations);

        // When
        ResponseEntity<ErrorResponse> response = globalExceptionHandler.handleConstraintViolationException(exception, webRequest);

        // Then
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Constraint Violation", response.getBody().getError());
    }

    @Test
    void handleCommandExecutionException_ShouldReturnInternalServerError() {
        // Given
        CommandExecutionException exception = new CommandExecutionException("Command failed", new RuntimeException("Root cause"));

        // When
        ResponseEntity<ErrorResponse> response = globalExceptionHandler.handleCommandExecutionException(exception, webRequest);

        // Then
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Command Execution Failed", response.getBody().getError());
    }

    @Test
    void handleCommandExecutionException_WithIllegalArgument_ShouldReturnBadRequest() {
        // Given
        CommandExecutionException exception = new CommandExecutionException("Command failed", new IllegalArgumentException("Invalid argument"));

        // When
        ResponseEntity<ErrorResponse> response = globalExceptionHandler.handleCommandExecutionException(exception, webRequest);

        // Then
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Command Execution Failed", response.getBody().getError());
    }

    @Test
    void handleQueryExecutionException_ShouldReturnInternalServerError() {
        // Given
        QueryExecutionException exception = new QueryExecutionException("Query failed", new RuntimeException("Root cause"));

        // When
        ResponseEntity<ErrorResponse> response = globalExceptionHandler.handleQueryExecutionException(exception, webRequest);

        // Then
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Query Execution Failed", response.getBody().getError());
    }

    @Test
    void handleOptimisticLockingFailure_ShouldReturnConflict() {
        // Given
        OptimisticLockingFailureException exception = new OptimisticLockingFailureException("Optimistic lock failed");

        // When
        ResponseEntity<ErrorResponse> response = globalExceptionHandler.handleOptimisticLockingFailure(exception, webRequest);

        // Then
        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Concurrency Conflict", response.getBody().getError());
        assertTrue(response.getBody().getMessage().contains("retry"));
    }

    @Test
    void handleDataAccessException_ShouldReturnInternalServerError() {
        // Given
        DataAccessException exception = new DataAccessException("Database error") {};

        // When
        ResponseEntity<ErrorResponse> response = globalExceptionHandler.handleDataAccessException(exception, webRequest);

        // Then
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Database Error", response.getBody().getError());
    }

    @Test
    void handleKafkaException_ShouldReturnServiceUnavailable() {
        // Given
        KafkaException exception = new KafkaException("Kafka error");

        // When
        ResponseEntity<ErrorResponse> response = globalExceptionHandler.handleKafkaException(exception, webRequest);

        // Then
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Messaging Service Unavailable", response.getBody().getError());
    }

    @Test
    void handleTimeoutException_ShouldReturnRequestTimeout() {
        // Given
        TimeoutException exception = new TimeoutException("Request timeout");

        // When
        ResponseEntity<ErrorResponse> response = globalExceptionHandler.handleTimeoutException(exception, webRequest);

        // Then
        assertEquals(HttpStatus.REQUEST_TIMEOUT, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Request Timeout", response.getBody().getError());
    }

    @Test
    void handleGenericException_ShouldReturnInternalServerError() {
        // Given
        Exception exception = new Exception("Generic error");

        // When
        ResponseEntity<ErrorResponse> response = globalExceptionHandler.handleGenericException(exception, webRequest);

        // Then
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Internal Server Error", response.getBody().getError());
        assertEquals("An unexpected error occurred", response.getBody().getMessage());
    }
}