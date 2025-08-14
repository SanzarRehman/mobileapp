package com.example.mainapplication.integration;

import com.example.mainapplication.command.CreateUserCommand;
import com.example.mainapplication.command.UpdateUserCommand;
import com.example.mainapplication.dto.CreateUserRequest;
import com.example.mainapplication.dto.UpdateUserRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration test for the complete command processing flow.
 * Tests the entire pipeline from REST API to command handlers.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CommandProcessingFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CommandGateway commandGateway;

    @Test
    void completeUserCreationFlow_ShouldProcessSuccessfully() throws Exception {
        // Given
        CreateUserRequest request = new CreateUserRequest(
                "integrationuser",
                "integration@example.com",
                "Integration Test User"
        );

        // When - Create user via REST API
        String response = mockMvc.perform(post("/api/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("User created successfully"))
                .andExpect(jsonPath("$.id").exists())
                .andReturn()
                .getResponse()
                .getContentAsString();

        // Then - Verify response structure
        assertNotNull(response);
        assertTrue(response.contains("\"success\":true"));
        assertTrue(response.contains("User created successfully"));
    }

    @Test
    void completeUserUpdateFlow_ShouldProcessSuccessfully() throws Exception {
        // Given - First create a user directly via command gateway
        String userId = UUID.randomUUID().toString();
        CreateUserCommand createCommand = new CreateUserCommand(
                userId,
                "originaluser",
                "original@example.com",
                "Original User"
        );

        CompletableFuture<String> createResult = commandGateway.send(createCommand);
        assertNotNull(createResult.get()); // Wait for creation to complete

        // When - Update user via REST API
        UpdateUserRequest updateRequest = new UpdateUserRequest(
                "updateduser",
                "updated@example.com",
                "Updated User"
        );

        String response = mockMvc.perform(put("/api/users/{userId}", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("User updated successfully"))
                .andExpect(jsonPath("$.id").value(userId))
                .andReturn()
                .getResponse()
                .getContentAsString();

        // Then - Verify response structure
        assertNotNull(response);
        assertTrue(response.contains("\"success\":true"));
        assertTrue(response.contains("User updated successfully"));
    }

    @Test
    void commandValidationFlow_InvalidData_ShouldReturnValidationError() throws Exception {
        // Given - Invalid request with short username
        CreateUserRequest request = new CreateUserRequest(
                "ab", // Too short - should trigger validation
                "invalid-email", // Invalid email format
                "" // Empty full name
        );

        // When & Then - Should return validation error
        mockMvc.perform(post("/api/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void commandGatewayDirectAccess_ShouldProcessCommands() throws Exception {
        // Given
        String userId = UUID.randomUUID().toString();
        CreateUserCommand command = new CreateUserCommand(
                userId,
                "directuser",
                "direct@example.com",
                "Direct User"
        );

        // When - Send command directly via gateway
        CompletableFuture<String> result = commandGateway.send(command);

        // Then - Should complete successfully
        assertNotNull(result);
        String commandResult = result.get();
        assertNotNull(commandResult);
    }

    @Test
    void commandInterceptorFlow_ShouldLogAndValidateCommands() throws Exception {
        // Given
        CreateUserRequest request = new CreateUserRequest(
                "interceptoruser",
                "interceptor@example.com",
                "Interceptor Test User"
        );

        // When - Process command through full pipeline
        mockMvc.perform(post("/api/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true));

        // Then - Command should have been processed through interceptors
        // (Logging and validation would be verified through log analysis in real scenario)
    }

    @Test
    void errorHandlingFlow_CommandFailure_ShouldReturnErrorResponse() throws Exception {
        // Given - Create user first
        String userId = UUID.randomUUID().toString();
        CreateUserCommand createCommand = new CreateUserCommand(
                userId,
                "erroruser",
                "error@example.com",
                "Error User"
        );
        commandGateway.send(createCommand).get();

        // When - Try to update with invalid data that will cause business rule violation
        UpdateUserRequest updateRequest = new UpdateUserRequest(
                "ab", // Too short - will trigger validation
                "error@example.com",
                "Error User"
        );

        // Then - Should return error response
        mockMvc.perform(put("/api/users/{userId}", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isBadRequest());
    }
}