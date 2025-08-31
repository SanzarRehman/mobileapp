package com.example.customaxonserver.controller;

import com.example.customaxonserver.entity.EventEntity;
import com.example.customaxonserver.service.EventStoreService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(EventReplayController.class)
class EventReplayControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private EventStoreService eventStoreService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void testGetAllEventsForReplay_Success() throws Exception {
        // Arrange
        OffsetDateTime timestamp = OffsetDateTime.now();
        JsonNode eventData = objectMapper.createObjectNode().put("userId", "user-1");
        
        EventEntity event1 = new EventEntity("user-1", "UserAggregate", 1L, "UserCreatedEvent", eventData);
        event1.setId(1L);
        event1.setTimestamp(timestamp);
        
        EventEntity event2 = new EventEntity("user-2", "UserAggregate", 1L, "UserCreatedEvent", eventData);
        event2.setId(2L);
        event2.setTimestamp(timestamp.plusMinutes(1));

        when(eventStoreService.getEventsAfterTimestamp(any(OffsetDateTime.class)))
            .thenReturn(List.of(event1, event2));

        // Act & Assert
        mockMvc.perform(get("/api/events/replay/all"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].aggregateId").value("user-1"))
                .andExpect(jsonPath("$[0].eventType").value("UserCreatedEvent"))
                .andExpect(jsonPath("$[1].id").value(2))
                .andExpect(jsonPath("$[1].aggregateId").value("user-2"));
    }

    @Test
    void testGetEventsForAggregateReplay_Success() throws Exception {
        // Arrange
        String aggregateId = "user-1";
        OffsetDateTime timestamp = OffsetDateTime.now();
        JsonNode eventData = objectMapper.createObjectNode().put("userId", aggregateId);
        
        EventEntity event1 = new EventEntity(aggregateId, "UserAggregate", 1L, "UserCreatedEvent", eventData);
        event1.setId(1L);
        event1.setTimestamp(timestamp);
        
        EventEntity event2 = new EventEntity(aggregateId, "UserAggregate", 2L, "UserUpdatedEvent", eventData);
        event2.setId(2L);
        event2.setTimestamp(timestamp.plusMinutes(1));

        when(eventStoreService.getEventsForAggregate(aggregateId))
            .thenReturn(List.of(event1, event2));

        // Act & Assert
        mockMvc.perform(get("/api/events/replay/aggregate/{aggregateId}", aggregateId))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].aggregateId").value(aggregateId))
                .andExpect(jsonPath("$[0].sequenceNumber").value(1))
                .andExpect(jsonPath("$[1].aggregateId").value(aggregateId))
                .andExpect(jsonPath("$[1].sequenceNumber").value(2));
    }

    @Test
    void testGetEventsAfterTimestamp_Success() throws Exception {
        // Arrange
        OffsetDateTime timestamp = OffsetDateTime.parse("2024-01-01T10:00:00Z");
        JsonNode eventData = objectMapper.createObjectNode().put("userId", "user-1");
        
        EventEntity event = new EventEntity("user-1", "UserAggregate", 1L, "UserCreatedEvent", eventData);
        event.setId(1L);
        event.setTimestamp(timestamp.plusHours(1));

        when(eventStoreService.getEventsAfterTimestamp(timestamp))
            .thenReturn(List.of(event));

        // Act & Assert
        mockMvc.perform(get("/api/events/replay/after")
                .param("timestamp", "2024-01-01T10:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(1));
    }

    @Test
    void testGetEventsByAggregateType_Success() throws Exception {
        // Arrange
        String aggregateType = "UserAggregate";
        OffsetDateTime from = OffsetDateTime.parse("2024-01-01T00:00:00Z");
        OffsetDateTime to = OffsetDateTime.parse("2024-01-02T00:00:00Z");
        JsonNode eventData = objectMapper.createObjectNode().put("userId", "user-1");
        
        EventEntity event = new EventEntity("user-1", aggregateType, 1L, "UserCreatedEvent", eventData);
        event.setId(1L);
        event.setTimestamp(from.plusHours(1));

        when(eventStoreService.getEventsByAggregateType(eq(aggregateType), any(OffsetDateTime.class), any(OffsetDateTime.class)))
            .thenReturn(List.of(event));

        // Act & Assert
        mockMvc.perform(get("/api/events/replay/aggregate-type/{aggregateType}", aggregateType)
                .param("from", "2024-01-01T00:00:00Z")
                .param("to", "2024-01-02T00:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].aggregateType").value(aggregateType));
    }

    @Test
    void testGetEventsByAggregateType_WithoutTimeRange() throws Exception {
        // Arrange
        String aggregateType = "UserAggregate";
        JsonNode eventData = objectMapper.createObjectNode().put("userId", "user-1");
        
        EventEntity event = new EventEntity("user-1", aggregateType, 1L, "UserCreatedEvent", eventData);
        event.setId(1L);
        event.setTimestamp(OffsetDateTime.now());

        when(eventStoreService.getEventsByAggregateType(eq(aggregateType), any(OffsetDateTime.class), any(OffsetDateTime.class)))
            .thenReturn(List.of(event));

        // Act & Assert
        mockMvc.perform(get("/api/events/replay/aggregate-type/{aggregateType}", aggregateType))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void testGetEventsByEventType_Success() throws Exception {
        // Arrange
        String eventType = "UserCreatedEvent";
        OffsetDateTime from = OffsetDateTime.parse("2024-01-01T00:00:00Z");
        OffsetDateTime to = OffsetDateTime.parse("2024-01-02T00:00:00Z");
        JsonNode eventData = objectMapper.createObjectNode().put("userId", "user-1");
        
        EventEntity event = new EventEntity("user-1", "UserAggregate", 1L, eventType, eventData);
        event.setId(1L);
        event.setTimestamp(from.plusHours(1));

        when(eventStoreService.getEventsByEventType(eq(eventType), any(OffsetDateTime.class), any(OffsetDateTime.class)))
            .thenReturn(List.of(event));

        // Act & Assert
        mockMvc.perform(get("/api/events/replay/event-type/{eventType}", eventType)
                .param("from", "2024-01-01T00:00:00Z")
                .param("to", "2024-01-02T00:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].eventType").value(eventType));
    }

    @Test
    void testGetAllEventsForReplay_ServiceException() throws Exception {
        // Arrange
        when(eventStoreService.getEventsAfterTimestamp(any(OffsetDateTime.class)))
            .thenThrow(new RuntimeException("Database error"));

        // Act & Assert
        mockMvc.perform(get("/api/events/replay/all"))
                .andExpect(status().isInternalServerError());
    }

    @Test
    void testGetEventsForAggregateReplay_ServiceException() throws Exception {
        // Arrange
        String aggregateId = "user-1";
        when(eventStoreService.getEventsForAggregate(aggregateId))
            .thenThrow(new RuntimeException("Database error"));

        // Act & Assert
        mockMvc.perform(get("/api/events/replay/aggregate/{aggregateId}", aggregateId))
                .andExpect(status().isInternalServerError());
    }

    @Test
    void testGetEventsAfterTimestamp_InvalidTimestamp() throws Exception {
        // Act & Assert
        mockMvc.perform(get("/api/events/replay/after")
                .param("timestamp", "invalid-timestamp"))
                .andExpect(status().isBadRequest());
    }
}