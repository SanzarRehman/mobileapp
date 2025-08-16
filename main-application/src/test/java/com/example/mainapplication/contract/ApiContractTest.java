package com.example.mainapplication.contract;

import com.example.mainapplication.dto.CreateUserRequest;
import com.example.mainapplication.dto.UpdateUserRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebMvc
@ActiveProfiles("test")
public class ApiContractTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void createUserEndpointShouldFollowContract() throws Exception {
        // Given
        CreateUserRequest request = new CreateUserRequest();
        request.setName("John Doe");
        request.setEmail("john.doe@example.com");

        // When
        MvcResult result = mockMvc.perform(post("/api/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn();

        // Then - Verify response contract
        String responseBody = result.getResponse().getContentAsString();
        JsonNode response = objectMapper.readTree(responseBody);

        // Response should contain required fields
        assertThat(response.has("aggregateId")).isTrue();
        assertThat(response.has("timestamp")).isTrue();
        assertThat(response.get("aggregateId").asText()).isNotEmpty();
        assertThat(response.get("timestamp").asText()).isNotEmpty();

        // Aggregate ID should be valid UUID
        String aggregateId = response.get("aggregateId").asText();
        assertThat(() -> UUID.fromString(aggregateId)).doesNotThrowAnyException();
    }

    @Test
    void updateUserEndpointShouldFollowContract() throws Exception {
        // Given
        String userId = UUID.randomUUID().toString();
        UpdateUserRequest request = new UpdateUserRequest();
        request.setName("Jane Doe");
        request.setEmail("jane.doe@example.com");

        // When
        MvcResult result = mockMvc.perform(put("/api/users/" + userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn();

        // Then - Verify response contract
        String responseBody = result.getResponse().getContentAsString();
        JsonNode response = objectMapper.readTree(responseBody);

        assertThat(response.has("aggregateId")).isTrue();
        assertThat(response.has("timestamp")).isTrue();
        assertThat(response.get("aggregateId").asText()).isEqualTo(userId);
    }

    @Test
    void getUserEndpointShouldFollowContract() throws Exception {
        // Given
        String userId = UUID.randomUUID().toString();

        // When
        mockMvc.perform(get("/api/users/" + userId))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.name").exists())
                .andExpect(jsonPath("$.email").exists())
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.updatedAt").exists());
    }

    @Test
    void getAllUsersEndpointShouldFollowContract() throws Exception {
        // When
        mockMvc.perform(get("/api/users"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void errorResponsesShouldFollowContract() throws Exception {
        // Test validation error contract
        CreateUserRequest invalidRequest = new CreateUserRequest();
        // Missing required fields

        MvcResult result = mockMvc.perform(post("/api/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        JsonNode response = objectMapper.readTree(responseBody);

        // Error response should follow contract
        assertThat(response.has("error")).isTrue();
        assertThat(response.has("message")).isTrue();
        assertThat(response.has("timestamp")).isTrue();
        assertThat(response.has("path")).isTrue();
    }

    @Test
    void healthEndpointShouldFollowContract() throws Exception {
        // When
        MvcResult result = mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn();

        // Then
        String responseBody = result.getResponse().getContentAsString();
        JsonNode response = objectMapper.readTree(responseBody);

        assertThat(response.has("status")).isTrue();
        assertThat(response.get("status").asText()).isIn("UP", "DOWN", "OUT_OF_SERVICE");
    }

    @Test
    void projectionRebuildEndpointShouldFollowContract() throws Exception {
        // When
        MvcResult result = mockMvc.perform(post("/api/projections/rebuild")
                .param("projectionName", "UserProjection"))
                .andExpect(status().isAccepted())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn();

        // Then
        String responseBody = result.getResponse().getContentAsString();
        JsonNode response = objectMapper.readTree(responseBody);

        assertThat(response.has("message")).isTrue();
        assertThat(response.has("projectionName")).isTrue();
        assertThat(response.has("timestamp")).isTrue();
    }

    @Test
    void contentTypeHeadersShouldBeConsistent() throws Exception {
        // All JSON endpoints should return proper content type
        CreateUserRequest request = new CreateUserRequest();
        request.setName("Test User");
        request.setEmail("test@example.com");

        mockMvc.perform(post("/api/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(header().string("Content-Type", "application/json"));

        mockMvc.perform(get("/api/users"))
                .andExpect(header().string("Content-Type", "application/json"));

        mockMvc.perform(get("/actuator/health"))
                .andExpect(header().string("Content-Type", "application/json"));
    }

    @Test
    void corsHeadersShouldBePresent() throws Exception {
        // When making OPTIONS request
        mockMvc.perform(options("/api/users")
                .header("Origin", "http://localhost:3000")
                .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isOk())
                .andExpect(header().exists("Access-Control-Allow-Origin"))
                .andExpect(header().exists("Access-Control-Allow-Methods"));
    }

    @Test
    void apiVersioningShouldBeConsistent() throws Exception {
        // All API endpoints should be under /api prefix
        CreateUserRequest request = new CreateUserRequest();
        request.setName("Version Test");
        request.setEmail("version@example.com");

        // Verify API endpoints follow versioning pattern
        mockMvc.perform(post("/api/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted());

        mockMvc.perform(get("/api/users"))
                .andExpect(status().isOk());

        // Non-API endpoints should not be under /api
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }
}