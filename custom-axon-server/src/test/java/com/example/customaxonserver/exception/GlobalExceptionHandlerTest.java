package com.example.customaxonserver.exception;

import com.example.customaxonserver.model.ErrorResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.redis.RedisConnectionFailureException;
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
    void handleCommandRoutingException_ShouldReturnServiceUnavailable() {
        // Given
        CommandRoutingException exception = new CommandRoutingException("Command routing failed");

        // When
        ResponseEntity<ErrorResponse> response = globalExceptionHandler.handleCommandRoutingException(exception, webRequest);

        // Then
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Command Routing Failed", response.getBody().getError());
        assertEquals("Command routing failed", response.getBody().getMessage());
    }

    @Test
    void handleQueryRoutingException_ShouldReturnServiceUnavailable() {
        // Given
        QueryRoutingException exception = new QueryRoutingException("Query routing failed");

        // When
        ResponseEntity<ErrorResponse> response = globalExceptionHandler.handleQueryRoutingException(exception, webRequest);

        // Then
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Query Routing Failed", response.getBody().getError());
        assertEquals("Query routing failed", response.getBody().getMessage());
    }

    @Test
    void handleEventStoreException_ShouldReturnInternalServerError() {
        // Given
        EventStoreException exception = new EventStoreException("Event store failed");

        // When
        ResponseEntity<ErrorResponse> response = globalExceptionHandler.handleEventStoreException(exception, webRequest);

        // Then
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Event Store Error", response.getBody().getError());
        assertEquals("Event store failed", response.getBody().getMessage());
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
    void handleRedisConnectionFailure_ShouldReturnServiceUnavailable() {
        // Given
        RedisConnectionFailureException exception = new RedisConnectionFailureException("Redis connection failed");

        // When
        ResponseEntity<ErrorResponse> response = globalExceptionHandler.handleRedisConnectionFailure(exception, webRequest);

        // Then
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Cache Service Unavailable", response.getBody().getError());
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