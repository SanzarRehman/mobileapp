package com.example.customaxonserver.integration;

import com.example.customaxonserver.resilience.CircuitBreakerService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    @Test
    void routeCommand_WithValidationError_ShouldReturnBadRequest() throws Exception {
        // Given - Invalid request with missing required fields
        String invalidRequest = "{}";

        // When & Then
        mockMvc.perform(post("/api/commands")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidRequest))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation Failed"))
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void routeQuery_WithValidationError_ShouldReturnBadRequest() throws Exception {
        // Given - Invalid request with missing required fields
        String invalidRequest = "{}";

        // When & Then
        mockMvc.perform(post("/api/queries")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidRequest))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation Failed"))
                .andExpect(jsonPath("$.status").value(400));
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
        // Given - Request that will cause an unexpected error
        String requestWithUnexpectedError = """
                {
                    "commandType": "NonExistentCommand",
                    "commandId": "test-id",
                    "payload": {},
                    "metadata": {}
                }
                """;

        // When & Then
        mockMvc.perform(post("/api/commands")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestWithUnexpectedError))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").exists())
                .andExpect(jsonPath("$.correlationId").exists());
    }
}