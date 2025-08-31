package com.example.customaxonserver.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for KafkaEventMessage model.
 * Verifies serialization, deserialization, and equality behavior.
 */
class KafkaEventMessageTest {

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    @Test
    void shouldCreateKafkaEventMessageWithAllFields() {
        // Given
        String eventId = "event-123";
        String aggregateId = "aggregate-456";
        String aggregateType = "UserAggregate";
        Long sequenceNumber = 1L;
        String eventType = "UserCreatedEvent";
        Object eventData = Map.of("userId", "user-123", "email", "test@example.com");
        Map<String, Object> metadata = Map.of("source", "test", "version", "1.0");
        Instant timestamp = Instant.now();

        // When
        KafkaEventMessage message = new KafkaEventMessage(
            eventId, aggregateId, aggregateType, sequenceNumber,
            eventType, eventData, metadata, timestamp
        );

        // Then
        assertThat(message.getEventId()).isEqualTo(eventId);
        assertThat(message.getAggregateId()).isEqualTo(aggregateId);
        assertThat(message.getAggregateType()).isEqualTo(aggregateType);
        assertThat(message.getSequenceNumber()).isEqualTo(sequenceNumber);
        assertThat(message.getEventType()).isEqualTo(eventType);
        assertThat(message.getEventData()).isEqualTo(eventData);
        assertThat(message.getMetadata()).isEqualTo(metadata);
        assertThat(message.getTimestamp()).isEqualTo(timestamp);
    }

    @Test
    void shouldSerializeAndDeserializeCorrectly() throws Exception {
        // Given
        KafkaEventMessage originalMessage = createTestMessage();

        // When
        String json = objectMapper.writeValueAsString(originalMessage);
        KafkaEventMessage deserializedMessage = objectMapper.readValue(json, KafkaEventMessage.class);

        // Then
        assertThat(deserializedMessage).isEqualTo(originalMessage);
        assertThat(deserializedMessage.getEventId()).isEqualTo(originalMessage.getEventId());
        assertThat(deserializedMessage.getAggregateId()).isEqualTo(originalMessage.getAggregateId());
        assertThat(deserializedMessage.getAggregateType()).isEqualTo(originalMessage.getAggregateType());
        assertThat(deserializedMessage.getSequenceNumber()).isEqualTo(originalMessage.getSequenceNumber());
        assertThat(deserializedMessage.getEventType()).isEqualTo(originalMessage.getEventType());
        assertThat(deserializedMessage.getEventData()).isEqualTo(originalMessage.getEventData());
        assertThat(deserializedMessage.getMetadata()).isEqualTo(originalMessage.getMetadata());
        assertThat(deserializedMessage.getTimestamp()).isEqualTo(originalMessage.getTimestamp());
    }

    @Test
    void shouldHandleComplexEventData() throws Exception {
        // Given
        Map<String, Object> complexEventData = Map.of(
            "user", Map.of(
                "id", "user-123",
                "profile", Map.of(
                    "firstName", "John",
                    "lastName", "Doe",
                    "preferences", Map.of("theme", "dark", "notifications", true)
                )
            ),
            "metadata", Map.of("version", 2, "migrated", false)
        );

        KafkaEventMessage message = new KafkaEventMessage(
            "event-123", "aggregate-456", "UserAggregate", 1L,
            "UserCreatedEvent", complexEventData, Map.of(), Instant.now()
        );

        // When
        String json = objectMapper.writeValueAsString(message);
        KafkaEventMessage deserializedMessage = objectMapper.readValue(json, KafkaEventMessage.class);

        // Then
        assertThat(deserializedMessage.getEventData()).isEqualTo(complexEventData);
    }

    @Test
    void shouldImplementEqualsAndHashCodeCorrectly() {
        // Given
        Instant timestamp = Instant.now();
        KafkaEventMessage message1 = new KafkaEventMessage(
            "event-123", "aggregate-456", "UserAggregate", 1L,
            "UserCreatedEvent", Map.of("key", "value"), Map.of("meta", "data"), timestamp
        );
        KafkaEventMessage message2 = new KafkaEventMessage(
            "event-123", "aggregate-456", "UserAggregate", 1L,
            "UserCreatedEvent", Map.of("key", "value"), Map.of("meta", "data"), timestamp
        );
        KafkaEventMessage message3 = new KafkaEventMessage(
            "event-456", "aggregate-456", "UserAggregate", 1L,
            "UserCreatedEvent", Map.of("key", "value"), Map.of("meta", "data"), timestamp
        );

        // Then
        assertThat(message1).isEqualTo(message2);
        assertThat(message1).isNotEqualTo(message3);
        assertThat(message1.hashCode()).isEqualTo(message2.hashCode());
        assertThat(message1.hashCode()).isNotEqualTo(message3.hashCode());
    }

    @Test
    void shouldHandleNullValues() throws Exception {
        // Given
        KafkaEventMessage message = new KafkaEventMessage(
            null, null, null, null, null, null, null, null
        );

        // When
        String json = objectMapper.writeValueAsString(message);
        KafkaEventMessage deserializedMessage = objectMapper.readValue(json, KafkaEventMessage.class);

        // Then
        assertThat(deserializedMessage).isEqualTo(message);
        assertThat(deserializedMessage.getEventId()).isNull();
        assertThat(deserializedMessage.getAggregateId()).isNull();
        assertThat(deserializedMessage.getAggregateType()).isNull();
        assertThat(deserializedMessage.getSequenceNumber()).isNull();
        assertThat(deserializedMessage.getEventType()).isNull();
        assertThat(deserializedMessage.getEventData()).isNull();
        assertThat(deserializedMessage.getMetadata()).isNull();
        assertThat(deserializedMessage.getTimestamp()).isNull();
    }

    @Test
    void shouldProvideReadableToString() {
        // Given
        KafkaEventMessage message = createTestMessage();

        // When
        String toString = message.toString();

        // Then
        assertThat(toString).contains("KafkaEventMessage{");
        assertThat(toString).contains("eventId='event-123'");
        assertThat(toString).contains("aggregateId='aggregate-456'");
        assertThat(toString).contains("aggregateType='UserAggregate'");
        assertThat(toString).contains("sequenceNumber=1");
        assertThat(toString).contains("eventType='UserCreatedEvent'");
    }

    private KafkaEventMessage createTestMessage() {
        return new KafkaEventMessage(
            "event-123",
            "aggregate-456",
            "UserAggregate",
            1L,
            "UserCreatedEvent",
            Map.of("userId", "user-123", "email", "test@example.com"),
            Map.of("source", "test", "version", "1.0"),
            Instant.parse("2024-01-01T10:00:00Z")
        );
    }
}