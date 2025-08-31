package com.example.customaxonserver.contract;

import com.example.customaxonserver.model.CommandMessage;
import com.example.customaxonserver.model.QueryMessage;
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

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebMvc
@ActiveProfiles("test")
public class CustomServerApiContractTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void commandEndpointShouldFollowContract() throws Exception {
        // Given
        CommandMessage command = new CommandMessage();
        command.setCommandId(UUID.randomUUID().toString());
        command.setCommandType("CreateUserCommand");
        command.setAggregateId(UUID.randomUUID().toString());
        command.setPayload(Map.of("name", "John Doe", "email", "john@example.com"));
        command.setMetadata(Map.of("correlationId", UUID.randomUUID().toString()));

        // When
        MvcResult result = mockMvc.perform(post("/api/commands")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(command)))
                .andExpect(status().isAccepted())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn();

        // Then - Verify response contract
        String responseBody = result.getResponse().getContentAsString();
        JsonNode response = objectMapper.readTree(responseBody);

        assertThat(response.has("commandId")).isTrue();
        assertThat(response.has("status")).isTrue();
        assertThat(response.has("timestamp")).isTrue();
        assertThat(response.get("commandId").asText()).isEqualTo(command.getCommandId());
        assertThat(response.get("status").asText()).isIn("ACCEPTED", "PROCESSED", "FAILED");
    }

    @Test
    void queryEndpointShouldFollowContract() throws Exception {
        // Given
        QueryMessage query = new QueryMessage();
        query.setQueryId(UUID.randomUUID().toString());
        query.setQueryType("FindUserByIdQuery");
        query.setPayload(Map.of("userId", UUID.randomUUID().toString()));
        query.setMetadata(Map.of("correlationId", UUID.randomUUID().toString()));

        // When
        MvcResult result = mockMvc.perform(post("/api/queries")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(query)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn();

        // Then - Verify response contract
        String responseBody = result.getResponse().getContentAsString();
        JsonNode response = objectMapper.readTree(responseBody);

        assertThat(response.has("queryId")).isTrue();
        assertThat(response.has("result")).isTrue();
        assertThat(response.has("timestamp")).isTrue();
        assertThat(response.get("queryId").asText()).isEqualTo(query.getQueryId());
    }

    @Test
    void eventsEndpointShouldFollowContract() throws Exception {
        // Given
        String aggregateId = UUID.randomUUID().toString();

        // When
        MvcResult result = mockMvc.perform(get("/api/events/{aggregateId}", aggregateId)
                .param("fromSequence", "0")
                .param("toSequence", "10"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn();

        // Then - Verify response contract
        String responseBody = result.getResponse().getContentAsString();
        JsonNode response = objectMapper.readTree(responseBody);

        assertThat(response.isArray()).isTrue();
        // Each event should follow contract if any exist
        if (response.size() > 0) {
            JsonNode event = response.get(0);
            assertThat(event.has("eventId")).isTrue();
            assertThat(event.has("aggregateId")).isTrue();
            assertThat(event.has("sequenceNumber")).isTrue();
            assertThat(event.has("eventType")).isTrue();
            assertThat(event.has("eventData")).isTrue();
            assertThat(event.has("timestamp")).isTrue();
        }
    }

    @Test
    void snapshotEndpointShouldFollowContract() throws Exception {
        // Given
        String aggregateId = UUID.randomUUID().toString();

        // When
        mockMvc.perform(get("/api/snapshots/{aggregateId}", aggregateId))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.aggregateId").exists())
                .andExpect(jsonPath("$.sequenceNumber").exists())
                .andExpect(jsonPath("$.snapshotData").exists())
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void eventReplayEndpointShouldFollowContract() throws Exception {
        // When
        MvcResult result = mockMvc.perform(post("/api/events/replay")
                .param("fromTimestamp", "2024-01-01T00:00:00Z")
                .param("toTimestamp", "2024-12-31T23:59:59Z"))
                .andExpect(status().isAccepted())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn();

        // Then - Verify response contract
        String responseBody = result.getResponse().getContentAsString();
        JsonNode response = objectMapper.readTree(responseBody);

        assertThat(response.has("replayId")).isTrue();
        assertThat(response.has("status")).isTrue();
        assertThat(response.has("fromTimestamp")).isTrue();
        assertThat(response.has("toTimestamp")).isTrue();
        assertThat(response.has("timestamp")).isTrue();
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
        
        // Should have component health details
        if (response.has("components")) {
            JsonNode components = response.get("components");
            // Verify expected health indicators
            assertThat(components.has("db")).isTrue();
            assertThat(components.has("kafka")).isTrue();
            assertThat(components.has("redis")).isTrue();
        }
    }

    @Test
    void errorResponsesShouldFollowContract() throws Exception {
        // Test invalid command format
        Map<String, Object> invalidCommand = new HashMap<>();
        invalidCommand.put("invalid", "data");

        MvcResult result = mockMvc.perform(post("/api/commands")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidCommand)))
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
    void metricsEndpointShouldFollowContract() throws Exception {
        // When
        mockMvc.perform(get("/actuator/metrics"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.names").isArray());

        // Test specific metric
        mockMvc.perform(get("/actuator/metrics/jvm.memory.used"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.name").value("jvm.memory.used"))
                .andExpect(jsonPath("$.measurements").isArray());
    }

    @Test
    void corsHeadersShouldBePresent() throws Exception {
        // When making OPTIONS request
        mockMvc.perform(options("/api/commands")
                .header("Origin", "http://localhost:3000")
                .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isOk())
                .andExpect(header().exists("Access-Control-Allow-Origin"))
                .andExpect(header().exists("Access-Control-Allow-Methods"));
    }

    @Test
    void securityHeadersShouldBePresent() throws Exception {
        // When making any request
        mockMvc.perform(get("/actuator/health"))
                .andExpect(header().exists("X-Content-Type-Options"))
                .andExpect(header().exists("X-Frame-Options"))
                .andExpect(header().exists("X-XSS-Protection"));
    }

    @Test
    void apiVersioningShouldBeConsistent() throws Exception {
        // All API endpoints should be under /api prefix
        CommandMessage command = new CommandMessage();
        command.setCommandId(UUID.randomUUID().toString());
        command.setCommandType("TestCommand");
        command.setAggregateId(UUID.randomUUID().toString());
        command.setPayload(Map.of("test", "data"));

        // Verify API endpoints follow versioning pattern
        mockMvc.perform(post("/api/commands")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(command)))
                .andExpect(status().isAccepted());

        // Non-API endpoints should not be under /api
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/actuator/metrics"))
                .andExpect(status().isOk());
    }
}