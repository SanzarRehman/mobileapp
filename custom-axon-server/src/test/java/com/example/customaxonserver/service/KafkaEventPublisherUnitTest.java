package com.example.customaxonserver.service;

import com.example.customaxonserver.config.KafkaConfig;
import com.example.customaxonserver.entity.EventEntity;
import com.example.customaxonserver.model.KafkaEventMessage;
import com.example.customaxonserver.util.PartitioningStrategy;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for KafkaEventPublisher using mocks.
 * Tests the core functionality without requiring embedded Kafka.
 */
@ExtendWith(MockitoExtension.class)
class KafkaEventPublisherUnitTest {

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Mock
    private KafkaConfig kafkaConfig;

    @Mock
    private SendResult<String, Object> sendResult;

    private PartitioningStrategy partitioningStrategy;
    private ObjectMapper objectMapper;
    private KafkaEventPublisher kafkaEventPublisher;

    @BeforeEach
    void setUp() {
        partitioningStrategy = new PartitioningStrategy();
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        kafkaEventPublisher = new KafkaEventPublisher(kafkaTemplate, kafkaConfig, partitioningStrategy, objectMapper);
    }

    @Test
    void shouldPublishEventWithCorrectParameters() {
        // Given
        EventEntity eventEntity = createTestEventEntity();
        CompletableFuture<SendResult<String, Object>> future = CompletableFuture.completedFuture(sendResult);
        
        when(kafkaConfig.getEventsTopicName()).thenReturn("test-events");
        when(kafkaTemplate.send(any(String.class), any(String.class), any(Object.class))).thenReturn(future);

        // When
        kafkaEventPublisher.publishEvent(eventEntity);

        // Then
        ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object> messageCaptor = ArgumentCaptor.forClass(Object.class);
        
        verify(kafkaTemplate).send(topicCaptor.capture(), keyCaptor.capture(), messageCaptor.capture());
        
        assertThat(topicCaptor.getValue()).isEqualTo("test-events");
        assertThat(keyCaptor.getValue()).isEqualTo("test-aggregate-123");
        
        KafkaEventMessage capturedMessage = (KafkaEventMessage) messageCaptor.getValue();
        assertThat(capturedMessage.getAggregateId()).isEqualTo("test-aggregate-123");
        assertThat(capturedMessage.getEventType()).isEqualTo("UserCreatedEvent");
        assertThat(capturedMessage.getSequenceNumber()).isEqualTo(1L);
    }

    @Test
    void shouldUsePartitioningStrategyForKey() {
        // Given
        EventEntity eventEntity = createTestEventEntity();
        eventEntity.setAggregateId("custom-aggregate-456");
        CompletableFuture<SendResult<String, Object>> future = CompletableFuture.completedFuture(sendResult);
        
        when(kafkaConfig.getEventsTopicName()).thenReturn("test-events");
        when(kafkaTemplate.send(any(String.class), any(String.class), any(Object.class))).thenReturn(future);

        // When
        kafkaEventPublisher.publishEvent(eventEntity);

        // Then
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(any(String.class), keyCaptor.capture(), any(Object.class));
        
        String expectedKey = partitioningStrategy.getPartitionKey("custom-aggregate-456");
        assertThat(keyCaptor.getValue()).isEqualTo(expectedKey);
    }

    @Test
    void shouldHandleNullAggregateId() {
        // Given
        EventEntity eventEntity = createTestEventEntity();
        eventEntity.setAggregateId(null);
        CompletableFuture<SendResult<String, Object>> future = CompletableFuture.completedFuture(sendResult);
        
        when(kafkaConfig.getEventsTopicName()).thenReturn("test-events");
        when(kafkaTemplate.send(any(String.class), any(String.class), any(Object.class))).thenReturn(future);

        // When
        kafkaEventPublisher.publishEvent(eventEntity);

        // Then
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(any(String.class), keyCaptor.capture(), any(Object.class));
        
        assertThat(keyCaptor.getValue()).isEqualTo("default");
    }

    @Test
    void shouldIncludeAdditionalMetadata() {
        // Given
        EventEntity eventEntity = createTestEventEntity();
        Map<String, Object> additionalMetadata = Map.of("correlationId", "test-123", "userId", "user-456");
        CompletableFuture<SendResult<String, Object>> future = CompletableFuture.completedFuture(sendResult);
        
        when(kafkaConfig.getEventsTopicName()).thenReturn("test-events");
        when(kafkaTemplate.send(any(String.class), any(String.class), any(Object.class))).thenReturn(future);

        // When
        kafkaEventPublisher.publishEvent(eventEntity, additionalMetadata);

        // Then
        ArgumentCaptor<Object> messageCaptor = ArgumentCaptor.forClass(Object.class);
        verify(kafkaTemplate).send(any(String.class), any(String.class), messageCaptor.capture());
        
        KafkaEventMessage capturedMessage = (KafkaEventMessage) messageCaptor.getValue();
        assertThat(capturedMessage.getMetadata()).containsEntry("correlationId", "test-123");
        assertThat(capturedMessage.getMetadata()).containsEntry("userId", "user-456");
        assertThat(capturedMessage.getMetadata()).containsEntry("source", "test"); // Original metadata
    }

    @Test
    void shouldConvertJsonNodeToMap() {
        // Given
        EventEntity eventEntity = createTestEventEntity();
        CompletableFuture<SendResult<String, Object>> future = CompletableFuture.completedFuture(sendResult);
        
        when(kafkaConfig.getEventsTopicName()).thenReturn("test-events");
        when(kafkaTemplate.send(any(String.class), any(String.class), any(Object.class))).thenReturn(future);

        // When
        kafkaEventPublisher.publishEvent(eventEntity);

        // Then
        ArgumentCaptor<Object> messageCaptor = ArgumentCaptor.forClass(Object.class);
        verify(kafkaTemplate).send(any(String.class), any(String.class), messageCaptor.capture());
        
        KafkaEventMessage capturedMessage = (KafkaEventMessage) messageCaptor.getValue();
        assertThat(capturedMessage.getEventData()).isInstanceOf(Map.class);
        
        @SuppressWarnings("unchecked")
        Map<String, Object> eventData = (Map<String, Object>) capturedMessage.getEventData();
        assertThat(eventData).containsEntry("userId", "user-123");
        assertThat(eventData).containsEntry("email", "test@example.com");
    }

    private EventEntity createTestEventEntity() {
        EventEntity eventEntity = new EventEntity();
        eventEntity.setId(1L);
        eventEntity.setAggregateId("test-aggregate-123");
        eventEntity.setAggregateType("UserAggregate");
        eventEntity.setSequenceNumber(1L);
        eventEntity.setEventType("UserCreatedEvent");
        
        // Create JsonNode for event data
        Map<String, Object> eventDataMap = Map.of("userId", "user-123", "email", "test@example.com");
        JsonNode eventData = objectMapper.valueToTree(eventDataMap);
        eventEntity.setEventData(eventData);
        
        // Create JsonNode for metadata
        Map<String, Object> metadataMap = Map.of("source", "test", "version", "1.0");
        JsonNode metadata = objectMapper.valueToTree(metadataMap);
        eventEntity.setMetadata(metadata);
        
        eventEntity.setTimestamp(OffsetDateTime.now());
        return eventEntity;
    }
}