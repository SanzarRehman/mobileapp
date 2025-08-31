package com.example.mainapplication.integration;

import com.example.mainapplication.LIB.resilience.CircuitBreakerService;
import com.example.mainapplication.service.CustomServerCommandService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ErrorHandlingIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CircuitBreakerService circuitBreakerService;

    @MockBean
    private RestTemplate restTemplate;

    @Test
    void createUser_WithValidationError_ShouldReturnBadRequest() throws Exception {
        // Given - Invalid request with missing required fields
        String invalidRequest = "{}";

        // When & Then
        mockMvc.perform(post("/api/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidRequest))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation Failed"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.details").exists());
    }

    @Test
    void createUser_WithCustomServerUnavailable_ShouldReturnServiceUnavailable() throws Exception {
        // Given - Mock RestTemplate to throw ResourceAccessException
        when(restTemplate.exchange(anyString(), any(), any(), any(Class.class)))
                .thenThrow(new ResourceAccessException("Connection refused"));

        String validRequest = """
                {
                    "name": "John Doe",
                    "email": "john.doe@example.com"
                }
                """;

        // When & Then
        mockMvc.perform(post("/api/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validRequest))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("Service Unavailable"));
    }

    @Test
    void circuitBreaker_ShouldOpenAfterMultipleFailures() {
        // Given
        String serviceName = "test-circuit-breaker";

        // When - Execute multiple failing operations
        for (int i = 0; i < 10; i++) {
            try {
                circuitBreakerService.executeWithCircuitBreaker(serviceName, () -> {
                    throw new RuntimeException("Simulated failure");
                });
            } catch (Exception e) {
                // Expected failures
            }
        }

        // Then - Circuit should be open or half-open
        CircuitBreaker.State state = circuitBreakerService.getCircuitBreakerState(serviceName);
        assertEquals(true, state == CircuitBreaker.State.OPEN || state == CircuitBreaker.State.HALF_OPEN);
    }

    @Test
    void globalExceptionHandler_ShouldHandleUnexpectedExceptions() throws Exception {
        // Given - Mock RestTemplate to throw unexpected exception
        when(restTemplate.exchange(anyString(), any(), any(), any(Class.class)))
                .thenThrow(new RuntimeException("Unexpected error"));

        String validRequest = """
                {
                    "name": "John Doe",
                    "email": "john.doe@example.com"
                }
                """;

        // When & Then
        mockMvc.perform(post("/api/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validRequest))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("Internal Server Error"))
                .andExpect(jsonPath("$.message").value("An unexpected error occurred"))
                .andExpect(jsonPath("$.correlationId").exists());
    }
}