package com.example.customaxonserver.controller;

import com.example.customaxonserver.service.EventStoreService;
import com.example.customaxonserver.service.KafkaEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST controller for event operations.
 */
@RestController
@RequestMapping("/api/events")
public class EventController {

    private static final Logger logger = LoggerFactory.getLogger(EventController.class);
    private final EventStoreService eventStoreService;
    private final KafkaEventPublisher kafkaEventPublisher;

    @Autowired
    public EventController(EventStoreService eventStoreService, KafkaEventPublisher kafkaEventPublisher) {
        this.eventStoreService = eventStoreService;
        this.kafkaEventPublisher = kafkaEventPublisher;
    }

    /**
     * Publishes an event to the event store and distributes it via Kafka.
     * 
     * @param eventPayload The event data to publish
     * @return Success response
     */
    @PostMapping("/publish")
    public ResponseEntity<Map<String, Object>> publishEvent(@RequestBody Map<String, Object> eventPayload) {
        logger.info("Received event for publishing: {}", eventPayload.get("eventType"));
        
        try {
            // Extract event details
            String eventId = (String) eventPayload.get("eventId");
            String eventType = (String) eventPayload.get("eventType");
            String aggregateId = (String) eventPayload.get("aggregateId");
            String aggregateType = (String) eventPayload.get("aggregateType");
            Object payload = eventPayload.get("payload");
            @SuppressWarnings("unchecked")
            Map<String, Object> metadata = (Map<String, Object>) eventPayload.get("metadata");
            Long sequenceNumber = eventPayload.get("sequenceNumber") != null ? 
                    ((Number) eventPayload.get("sequenceNumber")).longValue() : 
                    eventStoreService.getNextSequenceNumber(aggregateId);
            
            // Store event in event store
            var storedEvent = eventStoreService.storeEvent(
                aggregateId,
                aggregateType != null ? aggregateType : "UserAggregate", // Default aggregate type
                sequenceNumber,
                eventType,
                payload,
                metadata
            );
            
            // Publish to Kafka for distribution
            kafkaEventPublisher.publishEvent(storedEvent);
            
            logger.info("Successfully stored and published event {} for aggregate {}", eventId, aggregateId);
            
            return ResponseEntity.ok(Map.of(
                "eventId", eventId,
                "status", "SUCCESS",
                "message", "Event published successfully"
            ));
            
        } catch (Exception e) {
            logger.error("Failed to publish event: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                        "status", "ERROR",
                        "message", "Failed to publish event: " + e.getMessage()
                    ));
        }
    }

    /**
     * Health check endpoint.
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
            "status", "UP",
            "timestamp", System.currentTimeMillis()
        ));
    }
}