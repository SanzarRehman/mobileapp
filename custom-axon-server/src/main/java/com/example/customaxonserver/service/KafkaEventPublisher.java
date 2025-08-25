//package com.example.customaxonserver.service;
//
//import com.example.customaxonserver.config.KafkaConfig;
//import com.example.customaxonserver.entity.EventEntity;
//import com.example.customaxonserver.model.KafkaEventMessage;
//import com.example.customaxonserver.util.PartitioningStrategy;
//import com.fasterxml.jackson.databind.JsonNode;
//import com.fasterxml.jackson.databind.ObjectMapper;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//import org.springframework.kafka.core.KafkaTemplate;
//import org.springframework.kafka.support.SendResult;
//import org.springframework.stereotype.Service;
//
//import java.time.Instant;
//import java.util.HashMap;
//import java.util.Map;
//import java.util.concurrent.CompletableFuture;
//
///**
// * Service responsible for publishing events to Kafka topics.
// * Handles event serialization, partitioning, and error handling.
// */
//@Service
//public class KafkaEventPublisher {
//
//    private static final Logger logger = LoggerFactory.getLogger(KafkaEventPublisher.class);
//
//    private final KafkaTemplate<String, Object> kafkaTemplate;
//    private final KafkaConfig kafkaConfig;
//    private final PartitioningStrategy partitioningStrategy;
//    private final ObjectMapper objectMapper;
//
//    public KafkaEventPublisher(KafkaTemplate<String, Object> kafkaTemplate,
//                              KafkaConfig kafkaConfig,
//                              PartitioningStrategy partitioningStrategy,
//                              ObjectMapper objectMapper) {
//        this.kafkaTemplate = kafkaTemplate;
//        this.kafkaConfig = kafkaConfig;
//        this.partitioningStrategy = partitioningStrategy;
//        this.objectMapper = objectMapper;
//    }
//
//    /**
//     * Publishes an event to the events topic.
//     * Uses aggregate ID for partitioning to ensure event ordering.
//     *
//     * @param eventEntity the event to publish
//     * @return CompletableFuture that completes when the event is published
//     */
//    public CompletableFuture<SendResult<String, Object>> publishEvent(EventEntity eventEntity) {
//        return publishEvent(eventEntity, Map.of());
//    }
//
//    /**
//     * Publishes an event to the events topic with additional metadata.
//     *
//     * @param eventEntity the event to publish
//     * @param additionalMetadata additional metadata to include
//     * @return CompletableFuture that completes when the event is published
//     */
//    public CompletableFuture<SendResult<String, Object>> publishEvent(EventEntity eventEntity,
//                                                                     Map<String, Object> additionalMetadata) {
//        try {
//            KafkaEventMessage kafkaMessage = createKafkaEventMessage(eventEntity, additionalMetadata);
//            String partitionKey = partitioningStrategy.getPartitionKey(eventEntity.getAggregateId());
//
//            logger.debug("Publishing event {} for aggregate {} to topic {} with partition key {}",
//                    eventEntity.getEventType(), eventEntity.getAggregateId(),
//                    kafkaConfig.getEventsTopicName(), partitionKey);
//
//            CompletableFuture<SendResult<String, Object>> future = kafkaTemplate.send(
//                    kafkaConfig.getEventsTopicName(),
//                    partitionKey,
//                    kafkaMessage
//            );
//
//            // Add success and failure callbacks
//            future.whenComplete((result, throwable) -> {
//                if (throwable != null) {
//                    logger.error("Failed to publish event {} for aggregate {}: {}",
//                            eventEntity.getEventType(), eventEntity.getAggregateId(),
//                            throwable.getMessage(), throwable);
//                } else {
//                    logger.debug("Successfully published event {} for aggregate {} to partition {}",
//                            eventEntity.getEventType(), eventEntity.getAggregateId(),
//                            result.getRecordMetadata().partition());
//                }
//            });
//
//            return future;
//
//        } catch (Exception e) {
//            logger.error("Error creating Kafka message for event {} of aggregate {}: {}",
//                    eventEntity.getEventType(), eventEntity.getAggregateId(), e.getMessage(), e);
//
//            CompletableFuture<SendResult<String, Object>> failedFuture = new CompletableFuture<>();
//            failedFuture.completeExceptionally(e);
//            return failedFuture;
//        }
//    }
//
//    /**
//     * Publishes multiple events as a batch operation.
//     * Each event is published individually but the method returns when all are complete.
//     *
//     * @param events the events to publish
//     * @return CompletableFuture that completes when all events are published
//     */
//    public CompletableFuture<Void> publishEvents(Iterable<EventEntity> events) {
//        CompletableFuture<?>[] futures = new CompletableFuture[0];
//
//        for (EventEntity event : events) {
//            CompletableFuture<SendResult<String, Object>> future = publishEvent(event);
//            futures = addToArray(futures, future);
//        }
//
//        return CompletableFuture.allOf(futures);
//    }
//
//    /**
//     * Creates a KafkaEventMessage from an EventEntity.
//     */
//    private KafkaEventMessage createKafkaEventMessage(EventEntity eventEntity,
//                                                     Map<String, Object> additionalMetadata) {
//        // Convert JsonNode metadata to Map
//        Map<String, Object> combinedMetadata = new HashMap<>();
//        if (eventEntity.getMetadata() != null) {
//            combinedMetadata = objectMapper.convertValue(eventEntity.getMetadata(), Map.class);
//        }
//
//        // Merge with additional metadata
//        if (additionalMetadata != null && !additionalMetadata.isEmpty()) {
//            combinedMetadata.putAll(additionalMetadata);
//        }
//
//        // Convert JsonNode eventData to Object
//        Object eventData = eventEntity.getEventData() != null ?
//            objectMapper.convertValue(eventEntity.getEventData(), Object.class) : null;
//
//        return new KafkaEventMessage(
//                eventEntity.getId().toString(),
//                eventEntity.getAggregateId(),
//                eventEntity.getAggregateType(),
//                eventEntity.getSequenceNumber(),
//                eventEntity.getEventType(),
//                eventData,
//                combinedMetadata,
//                eventEntity.getTimestamp().toInstant()
//        );
//    }
//
//    /**
//     * Utility method to add a CompletableFuture to an array.
//     */
//    private CompletableFuture<?>[] addToArray(CompletableFuture<?>[] array, CompletableFuture<?> future) {
//        CompletableFuture<?>[] newArray = new CompletableFuture[array.length + 1];
//        System.arraycopy(array, 0, newArray, 0, array.length);
//        newArray[array.length] = future;
//        return newArray;
//    }
//
//    /**
//     * Checks if the Kafka template is available and ready to send messages.
//     *
//     * @return true if Kafka is available
//     */
//    public boolean isKafkaAvailable() {
//        try {
//            // Simple check by getting metadata
//            kafkaTemplate.getProducerFactory().createProducer().partitionsFor(kafkaConfig.getEventsTopicName());
//            return true;
//        } catch (Exception e) {
//            logger.warn("Kafka is not available: {}", e.getMessage());
//            return false;
//        }
//    }
//}