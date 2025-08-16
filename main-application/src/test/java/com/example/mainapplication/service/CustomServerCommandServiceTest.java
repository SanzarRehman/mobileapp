package com.example.mainapplication.service;

import com.example.mainapplication.command.CreateUserCommand;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.GenericCommandMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

/**
 * Unit tests for CustomServerCommandService.
 */
@ExtendWith(MockitoExtension.class)
class CustomServerCommandServiceTest {

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private com.example.mainapplication.resilience.CircuitBreakerService circuitBreakerService;

    @Mock
    private com.example.mainapplication.resilience.RetryService retryService;

    @Mock
    private com.example.mainapplication.messaging.DeadLetterQueueHandler deadLetterQueueHandler;

    private CustomServerCommandService service;
    private final String customServerUrl = "http://localhost:8081";

    @BeforeEach
    void setUp() {
        service = new CustomServerCommandService(restTemplate, objectMapper, customServerUrl, 
                circuitBreakerService, retryService, deadLetterQueueHandler);
        
        // Mock the circuit breaker and retry to just execute the operation
        lenient().when(circuitBreakerService.executeWithCircuitBreaker(anyString(), any(java.util.function.Supplier.class))).thenAnswer(invocation -> {
            java.util.function.Supplier<?> supplier = invocation.getArgument(1);
            return supplier.get();
        });
        
        lenient().when(retryService.executeWithRetry(anyString(), any(java.util.function.Supplier.class))).thenAnswer(invocation -> {
            java.util.function.Supplier<?> supplier = invocation.getArgument(1);
            return supplier.get();
        });
    }

    @Test
    void routeCommand_SuccessfulResponse_ShouldReturnResult() {
        // Given
        CreateUserCommand command = new CreateUserCommand(
                "user-123",
                "testuser",
                "test@example.com",
                "Test User"
        );
        CommandMessage<CreateUserCommand> commandMessage = GenericCommandMessage.asCommandMessage(command);
        
        String expectedResponse = "Command processed successfully";
        ResponseEntity<String> responseEntity = new ResponseEntity<>(expectedResponse, HttpStatus.OK);
        
        when(restTemplate.exchange(
                eq(customServerUrl + "/api/commands"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(String.class)
        )).thenReturn(responseEntity);

        // When
        String result = service.routeCommand(commandMessage, String.class);

        // Then
        assertEquals(expectedResponse, result);
        verify(restTemplate).exchange(
                eq(customServerUrl + "/api/commands"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(String.class)
        );
    }

    @Test
    void routeCommand_FailureResponse_ShouldThrowException() {
        // Given
        CreateUserCommand command = new CreateUserCommand(
                "user-123",
                "testuser",
                "test@example.com",
                "Test User"
        );
        CommandMessage<CreateUserCommand> commandMessage = GenericCommandMessage.asCommandMessage(command);
        
        ResponseEntity<String> responseEntity = new ResponseEntity<>("Error", HttpStatus.INTERNAL_SERVER_ERROR);
        
        when(restTemplate.exchange(
                eq(customServerUrl + "/api/commands"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(String.class)
        )).thenReturn(responseEntity);

        // When & Then
        assertThrows(RuntimeException.class, () -> 
            service.routeCommand(commandMessage, String.class)
        );
    }

    @Test
    void isCustomServerAvailable_HealthCheckSuccessful_ShouldReturnTrue() {
        // Given
        ResponseEntity<String> responseEntity = new ResponseEntity<>("OK", HttpStatus.OK);
        
        when(restTemplate.getForEntity(
                eq(customServerUrl + "/actuator/health"),
                eq(String.class)
        )).thenReturn(responseEntity);

        // When
        boolean result = service.isCustomServerAvailable();

        // Then
        assertTrue(result);
    }

    @Test
    void isCustomServerAvailable_HealthCheckFailed_ShouldReturnFalse() {
        // Given
        when(restTemplate.getForEntity(
                eq(customServerUrl + "/actuator/health"),
                eq(String.class)
        )).thenThrow(new RuntimeException("Connection failed"));

        // When
        boolean result = service.isCustomServerAvailable();

        // Then
        assertFalse(result);
    }
}