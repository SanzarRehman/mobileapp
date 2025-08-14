package com.example.mainapplication.listener;

import com.example.mainapplication.event.UserCreatedEvent;
import com.example.mainapplication.event.UserUpdatedEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.axonframework.eventhandling.gateway.EventGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Kafka event listener that consumes events from the custom Axon server
 * and publishes them to the local Axon event bus for processing.
 */
@Component
public class KafkaEventListener {

    private static final Logger logger = LoggerFactory.getLogger(KafkaEventListener.class);

    private final EventGateway eventGateway;
    private final ObjectMapper objectMapper;

    @Autowired
    public KafkaEventListener(EventGateway eventGateway, ObjectMapper objectMapper) {
        this.eventGateway = eventGateway;
        this.objectMapper = objectMapper;
    }

    /**
     * Listens to user-created events from Kafka topic.
     */
    @KafkaListener(topics = "user-created-events", groupId = "main-application")
    public void handleUserCreatedEvent(
            @Payload String eventPayload,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        logger.info("Received UserCreatedEvent from topic: {}, partition: {}, offset: {}", 
                   topic, partition, offset);
        
        try {
            // Parse the JSON payload
            JsonNode eventNode = objectMapper.readTree(eventPayload);
            
            // Extract event data
            String userId = eventNode.get("userId").asText();
            String username = eventNode.get("username").asText();
            String email = eventNode.get("email").asText();
            String fullName = eventNode.get("fullName").asText();
            Instant createdAt = Instant.parse(eventNode.get("createdAt").asText());
            
            // Create the event object
            UserCreatedEvent event = new UserCreatedEvent(userId, username, email, fullName, createdAt);
            
            // Publish to local event bus
            eventGateway.publish(event);
            
            // Acknowledge the message
            acknowledgment.acknowledge();
            
            logger.info("Successfully processed UserCreatedEvent for user: {}", userId);
            
        } catch (Exception e) {
            logger.error("Error processing UserCreatedEvent from Kafka: {}", eventPayload, e);
            // Don't acknowledge on error - this will cause retry
            throw new RuntimeException("Failed to process UserCreatedEvent", e);
        }
    }

    /**
     * Listens to user-updated events from Kafka topic.
     */
    @KafkaListener(topics = "user-updated-events", groupId = "main-application")
    public void handleUserUpdatedEvent(
            @Payload String eventPayload,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        logger.info("Received UserUpdatedEvent from topic: {}, partition: {}, offset: {}", 
                   topic, partition, offset);
        
        try {
            // Parse the JSON payload
            JsonNode eventNode = objectMapper.readTree(eventPayload);
            
            // Extract event data
            String userId = eventNode.get("userId").asText();
            String username = eventNode.get("username").asText();
            String email = eventNode.get("email").asText();
            String fullName = eventNode.get("fullName").asText();
            Instant updatedAt = Instant.parse(eventNode.get("updatedAt").asText());
            
            // Create the event object
            UserUpdatedEvent event = new UserUpdatedEvent(userId, username, email, fullName, updatedAt);
            
            // Publish to local event bus
            eventGateway.publish(event);
            
            // Acknowledge the message
            acknowledgment.acknowledge();
            
            logger.info("Successfully processed UserUpdatedEvent for user: {}", userId);
            
        } catch (Exception e) {
            logger.error("Error processing UserUpdatedEvent from Kafka: {}", eventPayload, e);
            // Don't acknowledge on error - this will cause retry
            throw new RuntimeException("Failed to process UserUpdatedEvent", e);
        }
    }
}