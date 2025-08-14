package com.example.customaxonserver.service;

import com.example.customaxonserver.config.KafkaConfig;
import com.example.customaxonserver.entity.EventEntity;
import com.example.customaxonserver.model.KafkaEventMessage;
import com.example.customaxonserver.util.PartitioningStrategy;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.SendResult;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for KafkaEventPublisher.
 * Uses embedded Kafka broker for testing event publishing functionality.
 */
@SpringBootTest
@ActiveProfiles("test")
@EmbeddedKafka(
    partitions = 2,
    topics = {"test-axon-events", "test-axon-commands", "test-axon-snapshots"},
    brokerProperties = {
        "listeners=PLAINTEXT://localhost:9092",
        "port=9092"
    }
)
@DirtiesContext
class KafkaEventPublisherTest {

    @Autowired
    private KafkaEventPublisher kafkaEventPublisher;

    @Autowired
    private KafkaConfig kafkaConfig;

    @Autowired
    private PartitioningStrategy partitioningStrategy;

    @Autowired
    private EmbeddedKafkaBroker embeddedKafkaBroker;

    private Consumer<String, Object> consumer;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule());
        setupConsumer();
    }

    private void setupConsumer() {
        Map<String, Object> consumerProps = KafkaTestUtils.consumerProps("test-group", "true", embeddedKafkaBroker);
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        consumerProps.put(JsonDeserializer.TRUSTED_PACKAGES, "*");
        consumerProps.put(JsonDeserializer.VALUE_DEFAULT_TYPE, KafkaEventMessage.class);

        ConsumerFactory<String, Object> consumerFactory = new DefaultKafkaConsumerFactory<>(consumerProps);
        consumer = consumerFactory.createConsumer();
        consumer.subscribe(Collections.singletonList(kafkaConfig.getEventsTopicName()));
    }

    @Test
    void shouldPublishEventSuccessfully() throws Exception {
        // Given
        EventEntity eventEntity = createTestEventEntity();

        // When
        CompletableFuture<SendResult<String, Object>> future = kafkaEventPublisher.publishEvent(eventEntity);
        SendResult<String, Object> result = future.get(5, TimeUnit.SECONDS);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getRecordMetadata().topic()).isEqualTo(kafkaConfig.getEventsTopicName());
        
        // Verify the message was received
        ConsumerRecords<String, Object> records = consumer.poll(Duration.ofSeconds(5));
        assertThat(records.count()).isEqualTo(1);
        
        ConsumerRecord<String, Object> record = records.iterator().next();
        assertThat(record.key()).isEqualTo(eventEntity.getAggregateId());
        
        // Verify the message content
        KafkaEventMessage kafkaMessage = objectMapper.convertValue(record.value(), KafkaEventMessage.class);
        assertThat(kafkaMessage.getAggregateId()).isEqualTo(eventEntity.getAggregateId());
        assertThat(kafkaMessage.getAggregateType()).isEqualTo(eventEntity.getAggregateType());
        assertThat(kafkaMessage.getEventType()).isEqualTo(eventEntity.getEventType());
        assertThat(kafkaMessage.getSequenceNumber()).isEqualTo(eventEntity.getSequenceNumber());
    }

    @Test
    void shouldPublishEventWithAdditionalMetadata() throws Exception {
        // Given
        EventEntity eventEntity = createTestEventEntity();
        Map<String, Object> additionalMetadata = Map.of(
            "correlationId", "test-correlation-123",
            "userId", "user-456"
        );

        // When
        CompletableFuture<SendResult<String, Object>> future = 
            kafkaEventPublisher.publishEvent(eventEntity, additionalMetadata);
        future.get(5, TimeUnit.SECONDS);

        // Then
        ConsumerRecords<String, Object> records = consumer.poll(Duration.ofSeconds(5));
        assertThat(records.count()).isEqualTo(1);
        
        ConsumerRecord<String, Object> record = records.iterator().next();
        KafkaEventMessage kafkaMessage = objectMapper.convertValue(record.value(), KafkaEventMessage.class);
        
        assertThat(kafkaMessage.getMetadata()).containsEntry("correlationId", "test-correlation-123");
        assertThat(kafkaMessage.getMetadata()).containsEntry("userId", "user-456");
    }

    @Test
    void shouldUseCorrectPartitioningStrategy() throws Exception {
        // Given
        String aggregateId = "test-aggregate-123";
        EventEntity eventEntity = createTestEventEntity();
        eventEntity.setAggregateId(aggregateId);

        // When
        CompletableFuture<SendResult<String, Object>> future = kafkaEventPublisher.publishEvent(eventEntity);
        SendResult<String, Object> result = future.get(5, TimeUnit.SECONDS);

        // Then
        int expectedPartition = partitioningStrategy.getPartitionForAggregate(aggregateId, 2);
        assertThat(result.getRecordMetadata().partition()).isEqualTo(expectedPartition);
        
        // Verify the partition key
        ConsumerRecords<String, Object> records = consumer.poll(Duration.ofSeconds(5));
        ConsumerRecord<String, Object> record = records.iterator().next();
        assertThat(record.key()).isEqualTo(partitioningStrategy.getPartitionKey(aggregateId));
    }

    @Test
    void shouldPublishMultipleEventsSuccessfully() throws Exception {
        // Given
        List<EventEntity> events = List.of(
            createTestEventEntity("aggregate-1", 1L),
            createTestEventEntity("aggregate-2", 1L),
            createTestEventEntity("aggregate-1", 2L)
        );

        // When
        CompletableFuture<Void> future = kafkaEventPublisher.publishEvents(events);
        future.get(10, TimeUnit.SECONDS);

        // Then
        ConsumerRecords<String, Object> records = consumer.poll(Duration.ofSeconds(5));
        assertThat(records.count()).isEqualTo(3);
        
        // Verify all events were published
        Map<String, Integer> aggregateEventCounts = new HashMap<>();
        for (ConsumerRecord<String, Object> record : records) {
            KafkaEventMessage kafkaMessage = objectMapper.convertValue(record.value(), KafkaEventMessage.class);
            aggregateEventCounts.merge(kafkaMessage.getAggregateId(), 1, Integer::sum);
        }
        
        assertThat(aggregateEventCounts).containsEntry("aggregate-1", 2);
        assertThat(aggregateEventCounts).containsEntry("aggregate-2", 1);
    }

    @Test
    void shouldHandleEventWithNullAggregateId() throws Exception {
        // Given
        EventEntity eventEntity = createTestEventEntity();
        eventEntity.setAggregateId(null);

        // When
        CompletableFuture<SendResult<String, Object>> future = kafkaEventPublisher.publishEvent(eventEntity);
        SendResult<String, Object> result = future.get(5, TimeUnit.SECONDS);

        // Then
        assertThat(result).isNotNull();
        
        ConsumerRecords<String, Object> records = consumer.poll(Duration.ofSeconds(5));
        assertThat(records.count()).isEqualTo(1);
        
        ConsumerRecord<String, Object> record = records.iterator().next();
        assertThat(record.key()).isEqualTo("default"); // Default partition key
    }

    @Test
    void shouldCheckKafkaAvailability() {
        // When
        boolean isAvailable = kafkaEventPublisher.isKafkaAvailable();

        // Then
        assertThat(isAvailable).isTrue();
    }

    @Test
    void shouldHandleEventWithComplexEventData() throws Exception {
        // Given
        EventEntity eventEntity = createTestEventEntity();
        Map<String, Object> complexEventData = Map.of(
            "userId", "user-123",
            "email", "test@example.com",
            "profile", Map.of(
                "firstName", "John",
                "lastName", "Doe",
                "preferences", List.of("email", "sms")
            )
        );
        eventEntity.setEventData(objectMapper.valueToTree(complexEventData));

        // When
        CompletableFuture<SendResult<String, Object>> future = kafkaEventPublisher.publishEvent(eventEntity);
        future.get(5, TimeUnit.SECONDS);

        // Then
        ConsumerRecords<String, Object> records = consumer.poll(Duration.ofSeconds(5));
        assertThat(records.count()).isEqualTo(1);
        
        ConsumerRecord<String, Object> record = records.iterator().next();
        KafkaEventMessage kafkaMessage = objectMapper.convertValue(record.value(), KafkaEventMessage.class);
        
        @SuppressWarnings("unchecked")
        Map<String, Object> receivedEventData = (Map<String, Object>) kafkaMessage.getEventData();
        assertThat(receivedEventData).containsEntry("userId", "user-123");
        assertThat(receivedEventData).containsEntry("email", "test@example.com");
        assertThat(receivedEventData).containsKey("profile");
    }

    private EventEntity createTestEventEntity() {
        return createTestEventEntity("test-aggregate-123", 1L);
    }

    private EventEntity createTestEventEntity(String aggregateId, Long sequenceNumber) {
        EventEntity eventEntity = new EventEntity();
        eventEntity.setId(1L);
        eventEntity.setAggregateId(aggregateId);
        eventEntity.setAggregateType("UserAggregate");
        eventEntity.setSequenceNumber(sequenceNumber);
        eventEntity.setEventType("UserCreatedEvent");
        eventEntity.setEventData(objectMapper.valueToTree(Map.of("userId", "user-123", "email", "test@example.com")));
        eventEntity.setMetadata(objectMapper.valueToTree(Map.of("source", "test", "version", "1.0")));
        eventEntity.setTimestamp(OffsetDateTime.now());
        return eventEntity;
    }
}