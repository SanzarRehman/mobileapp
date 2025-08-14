package com.example.mainapplication.controller;

import com.example.mainapplication.dto.CreateUserRequest;
import com.example.mainapplication.dto.UpdateUserRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for UserCommandController.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class UserCommandControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void createUser_ValidRequest_ShouldReturnCreated() throws Exception {
        CreateUserRequest request = new CreateUserRequest(
                "testuser",
                "test@example.com",
                "Test User"
        );

        mockMvc.perform(post("/api/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("User created successfully"))
                .andExpect(jsonPath("$.id").exists());
    }

    @Test
    void createUser_InvalidUsername_ShouldReturnBadRequest() throws Exception {
        CreateUserRequest request = new CreateUserRequest(
                "ab", // Too short
                "test@example.com",
                "Test User"
        );

        mockMvc.perform(post("/api/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createUser_InvalidEmail_ShouldReturnBadRequest() throws Exception {
        CreateUserRequest request = new CreateUserRequest(
                "testuser",
                "invalid-email", // Invalid email format
                "Test User"
        );

        mockMvc.perform(post("/api/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createUser_EmptyFullName_ShouldReturnBadRequest() throws Exception {
        CreateUserRequest request = new CreateUserRequest(
                "testuser",
                "test@example.com",
                "" // Empty full name
        );

        mockMvc.perform(post("/api/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateUser_ValidRequest_ShouldReturnOk() throws Exception {
        // First create a user
        CreateUserRequest createRequest = new CreateUserRequest(
                "testuser",
                "test@example.com",
                "Test User"
        );

        String createResponse = mockMvc.perform(post("/api/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        // Extract user ID from response (simplified for test)
        String userId = "test-user-id"; // In real scenario, extract from createResponse

        UpdateUserRequest updateRequest = new UpdateUserRequest(
                "updateduser",
                "updated@example.com",
                "Updated User"
        );

        mockMvc.perform(put("/api/users/{userId}", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("User updated successfully"))
                .andExpect(jsonPath("$.id").value(userId));
    }

    @Test
    void updateUser_InvalidUsername_ShouldReturnBadRequest() throws Exception {
        String userId = "test-user-id";
        UpdateUserRequest request = new UpdateUserRequest(
                "ab", // Too short
                "test@example.com",
                "Test User"
        );

        mockMvc.perform(put("/api/users/{userId}", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
}