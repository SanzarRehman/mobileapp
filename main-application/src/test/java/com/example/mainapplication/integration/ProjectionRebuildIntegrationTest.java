package com.example.mainapplication.integration;

import com.example.mainapplication.projection.UserProjection;
import com.example.mainapplication.repository.UserProjectionRepository;
import com.example.mainapplication.service.ProjectionRebuildService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureWebMvc
@ActiveProfiles("test")
@Transactional
class ProjectionRebuildIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserProjectionRepository userProjectionRepository;

    @Autowired
    private ProjectionRebuildService projectionRebuildService;

    @MockBean
    private RestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        // Clear any existing projections
        userProjectionRepository.deleteAll();
    }

    @Test
    void testCompleteProjectionRebuildFlow() throws Exception {
        // Arrange - Create some existing projections
        UserProjection existingUser = new UserProjection("user-1", "Old Name", "old@example.com", "ACTIVE");
        userProjectionRepository.save(existingUser);
        
        assertEquals(1, userProjectionRepository.count());

        // Mock the custom server response with updated event data
        String eventsJson = """
            [
                {
                    "eventType": "UserCreatedEvent",
                    "eventData": {
                        "userId": "user-1",
                        "fullName": "John Doe",
                        "email": "john@example.com",
                        "createdAt": "2024-01-01T10:00:00Z"
                    }
                },
                {
                    "eventType": "UserUpdatedEvent",
                    "eventData": {
                        "userId": "user-1",
                        "fullName": "John Smith",
                        "email": "john.smith@example.com",
                        "updatedAt": "2024-01-02T10:00:00Z"
                    }
                },
                {
                    "eventType": "UserCreatedEvent",
                    "eventData": {
                        "userId": "user-2",
                        "fullName": "Jane Doe",
                        "email": "jane@example.com",
                        "createdAt": "2024-01-03T10:00:00Z"
                    }
                }
            ]
            """;

        ResponseEntity<com.fasterxml.jackson.databind.JsonNode> response = 
            new ResponseEntity<>(objectMapper.readTree(eventsJson), HttpStatus.OK);
        
        when(restTemplate.getForEntity(anyString(), eq(com.fasterxml.jackson.databind.JsonNode.class)))
            .thenReturn(response);

        // Act - Trigger rebuild via REST API
        String rebuildResponse = mockMvc.perform(post("/api/projections/rebuild/all")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.rebuildId").exists())
                .andReturn()
                .getResponse()
                .getContentAsString();

        // Extract rebuild ID from response
        com.fasterxml.jackson.databind.JsonNode responseNode = objectMapper.readTree(rebuildResponse);
        String rebuildId = responseNode.get("rebuildId").asText();

        // Wait for rebuild to complete (in real scenario, this would be polled)
        Thread.sleep(1000);

        // Assert - Check rebuild status
        mockMvc.perform(get("/api/projections/rebuild/status/{rebuildId}", rebuildId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.totalEvents").value(3))
                .andExpect(jsonPath("$.processedEvents").value(3))
                .andExpect(jsonPath("$.errorCount").value(0));

        // Assert - Verify projections were rebuilt correctly
        List<UserProjection> allUsers = userProjectionRepository.findAll();
        assertEquals(2, allUsers.size());

        UserProjection user1 = userProjectionRepository.findById("user-1").orElse(null);
        assertNotNull(user1);
        assertEquals("John Smith", user1.getName()); // Should have the updated name
        assertEquals("john.smith@example.com", user1.getEmail()); // Should have the updated email

        UserProjection user2 = userProjectionRepository.findById("user-2").orElse(null);
        assertNotNull(user2);
        assertEquals("Jane Doe", user2.getName());
        assertEquals("jane@example.com", user2.getEmail());
    }

    @Test
    void testAggregateSpecificRebuild() throws Exception {
        // Arrange - Create existing projections
        UserProjection user1 = new UserProjection("user-1", "Old Name 1", "old1@example.com", "ACTIVE");
        UserProjection user2 = new UserProjection("user-2", "Old Name 2", "old2@example.com", "ACTIVE");
        userProjectionRepository.saveAll(List.of(user1, user2));
        
        assertEquals(2, userProjectionRepository.count());

        // Mock response for specific aggregate
        String eventsJson = """
            [
                {
                    "eventType": "UserCreatedEvent",
                    "eventData": {
                        "userId": "user-1",
                        "fullName": "John Doe Updated",
                        "email": "john.updated@example.com",
                        "createdAt": "2024-01-01T10:00:00Z"
                    }
                }
            ]
            """;

        ResponseEntity<com.fasterxml.jackson.databind.JsonNode> response = 
            new ResponseEntity<>(objectMapper.readTree(eventsJson), HttpStatus.OK);
        
        when(restTemplate.getForEntity(anyString(), eq(com.fasterxml.jackson.databind.JsonNode.class)))
            .thenReturn(response);

        // Act - Trigger aggregate-specific rebuild
        String rebuildResponse = mockMvc.perform(post("/api/projections/rebuild/aggregate/{aggregateId}", "user-1")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.rebuildId").exists())
                .andReturn()
                .getResponse()
                .getContentAsString();

        // Wait for rebuild to complete
        Thread.sleep(1000);

        // Assert - Only user-1 should be updated, user-2 should remain unchanged
        List<UserProjection> allUsers = userProjectionRepository.findAll();
        assertEquals(2, allUsers.size()); // Still 2 users

        UserProjection updatedUser1 = userProjectionRepository.findById("user-1").orElse(null);
        assertNotNull(updatedUser1);
        assertEquals("John Doe Updated", updatedUser1.getName());
        assertEquals("john.updated@example.com", updatedUser1.getEmail());

        UserProjection unchangedUser2 = userProjectionRepository.findById("user-2").orElse(null);
        assertNotNull(unchangedUser2);
        assertEquals("Old Name 2", unchangedUser2.getName()); // Should remain unchanged
        assertEquals("old2@example.com", unchangedUser2.getEmail()); // Should remain unchanged
    }

    @Test
    void testRebuildWithCustomServerError() throws Exception {
        // Arrange - Mock server error
        when(restTemplate.getForEntity(anyString(), eq(com.fasterxml.jackson.databind.JsonNode.class)))
            .thenReturn(new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR));

        // Act & Assert - Should return error response
        mockMvc.perform(post("/api/projections/rebuild/all")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Failed to start rebuild")));
    }

    @Test
    void testGetAllRebuildStatuses() throws Exception {
        // Arrange - Start a rebuild to have some status
        String eventsJson = "[]";
        ResponseEntity<com.fasterxml.jackson.databind.JsonNode> response = 
            new ResponseEntity<>(objectMapper.readTree(eventsJson), HttpStatus.OK);
        
        when(restTemplate.getForEntity(anyString(), eq(com.fasterxml.jackson.databind.JsonNode.class)))
            .thenReturn(response);

        // Start a rebuild
        mockMvc.perform(post("/api/projections/rebuild/all")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isAccepted());

        // Wait a moment
        Thread.sleep(500);

        // Act & Assert - Get all statuses
        mockMvc.perform(get("/api/projections/rebuild/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isMap())
                .andExpect(jsonPath("$").isNotEmpty());
    }

    @Test
    void testRebuildWithInvalidEventData() throws Exception {
        // Arrange - Mock response with invalid event data
        String invalidEventsJson = """
            [
                {
                    "eventType": "UserCreatedEvent",
                    "eventData": {
                        "userId": "user-1"
                        // Missing required fields
                    }
                }
            ]
            """;

        ResponseEntity<com.fasterxml.jackson.databind.JsonNode> response = 
            new ResponseEntity<>(objectMapper.readTree(invalidEventsJson), HttpStatus.OK);
        
        when(restTemplate.getForEntity(anyString(), eq(com.fasterxml.jackson.databind.JsonNode.class)))
            .thenReturn(response);

        // Act - Trigger rebuild
        String rebuildResponse = mockMvc.perform(post("/api/projections/rebuild/all")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isAccepted())
                .andReturn()
                .getResponse()
                .getContentAsString();

        com.fasterxml.jackson.databind.JsonNode responseNode = objectMapper.readTree(rebuildResponse);
        String rebuildId = responseNode.get("rebuildId").asText();

        // Wait for rebuild to complete
        Thread.sleep(1000);

        // Assert - Should complete but with errors
        mockMvc.perform(get("/api/projections/rebuild/status/{rebuildId}", rebuildId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.errorCount").value(1)); // Should have 1 error
    }
}