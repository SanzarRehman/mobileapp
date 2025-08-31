package com.example.mainapplication.listener;

import com.example.mainapplication.event.UserCreatedEvent;
import com.example.mainapplication.event.UserUpdatedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.axonframework.eventhandling.gateway.EventGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KafkaEventListenerTest {

    @Mock
    private EventGateway eventGateway;

    @Mock
    private Acknowledgment acknowledgment;

    @InjectMocks
    private KafkaEventListener kafkaEventListener;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        kafkaEventListener = new KafkaEventListener(eventGateway, objectMapper);
    }

    @Test
    void shouldProcessUserCreatedEventFromKafka() {
        // Given
        String eventPayload = """
            {
                "userId": "user-123",
                "username": "johndoe",
                "email": "john.doe@example.com",
                "fullName": "John Doe",
                "createdAt": "2023-01-01T10:00:00Z"
            }
            """;

        // When
        kafkaEventListener.handleUserCreatedEvent(eventPayload, "user-created-events", 0, 100L, acknowledgment);

        // Then
        verify(eventGateway).publish((Object) argThat(event -> {
            if (event instanceof UserCreatedEvent userCreatedEvent) {
                return userCreatedEvent.getUserId().equals("user-123") &&
                       userCreatedEvent.getUsername().equals("johndoe") &&
                       userCreatedEvent.getEmail().equals("john.doe@example.com") &&
                       userCreatedEvent.getFullName().equals("John Doe") &&
                       userCreatedEvent.getCreatedAt().equals(Instant.parse("2023-01-01T10:00:00Z"));
            }
            return false;
        }));
        verify(acknowledgment).acknowledge();
    }

    @Test
    void shouldProcessUserUpdatedEventFromKafka() {
        // Given
        String eventPayload = """
            {
                "userId": "user-123",
                "username": "johndoe_updated",
                "email": "john.doe.updated@example.com",
                "fullName": "John Doe Updated",
                "updatedAt": "2023-01-01T11:00:00Z"
            }
            """;

        // When
        kafkaEventListener.handleUserUpdatedEvent(eventPayload, "user-updated-events", 0, 101L, acknowledgment);

        // Then
        verify(eventGateway).publish((Object) argThat(event -> {
            if (event instanceof UserUpdatedEvent userUpdatedEvent) {
                return userUpdatedEvent.getUserId().equals("user-123") &&
                       userUpdatedEvent.getUsername().equals("johndoe_updated") &&
                       userUpdatedEvent.getEmail().equals("john.doe.updated@example.com") &&
                       userUpdatedEvent.getFullName().equals("John Doe Updated") &&
                       userUpdatedEvent.getUpdatedAt().equals(Instant.parse("2023-01-01T11:00:00Z"));
            }
            return false;
        }));
        verify(acknowledgment).acknowledge();
    }

    @Test
    void shouldNotAcknowledgeOnUserCreatedEventProcessingError() {
        // Given
        String invalidPayload = "{ invalid json }";

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class, () ->
            kafkaEventListener.handleUserCreatedEvent(invalidPayload, "user-created-events", 0, 100L, acknowledgment)
        );

        assertEquals("Failed to process UserCreatedEvent", exception.getMessage());
        verify(eventGateway, never()).publish((Object) any());
        verify(acknowledgment, never()).acknowledge();
    }

    @Test
    void shouldNotAcknowledgeOnUserUpdatedEventProcessingError() {
        // Given
        String invalidPayload = "{ invalid json }";

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class, () ->
            kafkaEventListener.handleUserUpdatedEvent(invalidPayload, "user-updated-events", 0, 101L, acknowledgment)
        );

        assertEquals("Failed to process UserUpdatedEvent", exception.getMessage());
        verify(eventGateway, never()).publish((Object) any());
        verify(acknowledgment, never()).acknowledge();
    }

    @Test
    void shouldNotAcknowledgeWhenEventGatewayThrowsException() {
        // Given
        String eventPayload = """
            {
                "userId": "user-123",
                "username": "johndoe",
                "email": "john.doe@example.com",
                "fullName": "John Doe",
                "createdAt": "2023-01-01T10:00:00Z"
            }
            """;
        
        doThrow(new RuntimeException("Event gateway error")).when(eventGateway).publish((Object) any());

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class, () ->
            kafkaEventListener.handleUserCreatedEvent(eventPayload, "user-created-events", 0, 100L, acknowledgment)
        );

        assertEquals("Failed to process UserCreatedEvent", exception.getMessage());
        verify(acknowledgment, never()).acknowledge();
    }

    @Test
    void shouldHandleMissingFieldsInEventPayload() {
        // Given
        String incompletePayload = """
            {
                "userId": "user-123",
                "username": "johndoe"
            }
            """;

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class, () ->
            kafkaEventListener.handleUserCreatedEvent(incompletePayload, "user-created-events", 0, 100L, acknowledgment)
        );

        assertEquals("Failed to process UserCreatedEvent", exception.getMessage());
        verify(eventGateway, never()).publish((Object) any());
        verify(acknowledgment, never()).acknowledge();
    }
}