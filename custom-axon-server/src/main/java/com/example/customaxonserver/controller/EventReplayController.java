package com.example.customaxonserver.controller;

import com.example.customaxonserver.entity.EventEntity;
import com.example.customaxonserver.service.EventStoreService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * REST controller for event replay operations.
 * Provides endpoints for retrieving events for projection rebuilding.
 */
@RestController
@RequestMapping("/api/events/replay")
public class EventReplayController {

    private static final Logger logger = LoggerFactory.getLogger(EventReplayController.class);

    private final EventStoreService eventStoreService;

    @Autowired
    public EventReplayController(EventStoreService eventStoreService) {
        this.eventStoreService = eventStoreService;
    }

    /**
     * Retrieves all events for replay in chronological order.
     * This endpoint is used for complete projection rebuilds.
     *
     * @return List of all events ordered by timestamp
     */
    @GetMapping("/all")
    public ResponseEntity<List<EventReplayData>> getAllEventsForReplay() {
        logger.info("Received request to retrieve all events for replay");
        
        try {
            // Get all events after epoch (effectively all events)
            OffsetDateTime epochStart = OffsetDateTime.parse("1970-01-01T00:00:00Z");
            List<EventEntity> events = eventStoreService.getEventsAfterTimestamp(epochStart);
            
            List<EventReplayData> replayData = events.stream()
                .map(this::convertToReplayData)
                .collect(Collectors.toList());
            
            logger.info("Retrieved {} events for replay", replayData.size());
            return ResponseEntity.ok(replayData);
            
        } catch (Exception e) {
            logger.error("Failed to retrieve events for replay", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Retrieves events for a specific aggregate for replay.
     *
     * @param aggregateId The aggregate ID to retrieve events for
     * @return List of events for the specified aggregate
     */
    @GetMapping("/aggregate/{aggregateId}")
    public ResponseEntity<List<EventReplayData>> getEventsForAggregateReplay(
            @PathVariable String aggregateId) {
        logger.info("Received request to retrieve events for aggregate replay: {}", aggregateId);
        
        try {
            List<EventEntity> events = eventStoreService.getEventsForAggregate(aggregateId);
            
            List<EventReplayData> replayData = events.stream()
                .map(this::convertToReplayData)
                .collect(Collectors.toList());
            
            logger.info("Retrieved {} events for aggregate {} replay", replayData.size(), aggregateId);
            return ResponseEntity.ok(replayData);
            
        } catch (Exception e) {
            logger.error("Failed to retrieve events for aggregate {} replay", aggregateId, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Retrieves events after a specific timestamp for incremental replay.
     *
     * @param timestamp The timestamp to start from (ISO format)
     * @return List of events after the specified timestamp
     */
    @GetMapping("/after")
    public ResponseEntity<List<EventReplayData>> getEventsAfterTimestamp(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime timestamp) {
        logger.info("Received request to retrieve events after timestamp: {}", timestamp);
        
        try {
            List<EventEntity> events = eventStoreService.getEventsAfterTimestamp(timestamp);
            
            List<EventReplayData> replayData = events.stream()
                .map(this::convertToReplayData)
                .collect(Collectors.toList());
            
            logger.info("Retrieved {} events after timestamp {}", replayData.size(), timestamp);
            return ResponseEntity.ok(replayData);
            
        } catch (Exception e) {
            logger.error("Failed to retrieve events after timestamp {}", timestamp, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Retrieves events by aggregate type within a time range.
     *
     * @param aggregateType The aggregate type to filter by
     * @param from Start timestamp (optional)
     * @param to End timestamp (optional)
     * @return List of events for the specified aggregate type
     */
    @GetMapping("/aggregate-type/{aggregateType}")
    public ResponseEntity<List<EventReplayData>> getEventsByAggregateType(
            @PathVariable String aggregateType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to) {
        
        logger.info("Received request to retrieve events for aggregate type: {} from {} to {}", 
                   aggregateType, from, to);
        
        try {
            // Use default time range if not specified
            OffsetDateTime fromTime = from != null ? from : OffsetDateTime.parse("1970-01-01T00:00:00Z");
            OffsetDateTime toTime = to != null ? to : OffsetDateTime.now();
            
            List<EventEntity> events = eventStoreService.getEventsByAggregateType(aggregateType, fromTime, toTime);
            
            List<EventReplayData> replayData = events.stream()
                .map(this::convertToReplayData)
                .collect(Collectors.toList());
            
            logger.info("Retrieved {} events for aggregate type {} in time range", 
                       replayData.size(), aggregateType);
            return ResponseEntity.ok(replayData);
            
        } catch (Exception e) {
            logger.error("Failed to retrieve events for aggregate type {}", aggregateType, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Retrieves events by event type within a time range.
     *
     * @param eventType The event type to filter by
     * @param from Start timestamp (optional)
     * @param to End timestamp (optional)
     * @return List of events for the specified event type
     */
    @GetMapping("/event-type/{eventType}")
    public ResponseEntity<List<EventReplayData>> getEventsByEventType(
            @PathVariable String eventType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to) {
        
        logger.info("Received request to retrieve events for event type: {} from {} to {}", 
                   eventType, from, to);
        
        try {
            // Use default time range if not specified
            OffsetDateTime fromTime = from != null ? from : OffsetDateTime.parse("1970-01-01T00:00:00Z");
            OffsetDateTime toTime = to != null ? to : OffsetDateTime.now();
            
            List<EventEntity> events = eventStoreService.getEventsByEventType(eventType, fromTime, toTime);
            
            List<EventReplayData> replayData = events.stream()
                .map(this::convertToReplayData)
                .collect(Collectors.toList());
            
            logger.info("Retrieved {} events for event type {} in time range", 
                       replayData.size(), eventType);
            return ResponseEntity.ok(replayData);
            
        } catch (Exception e) {
            logger.error("Failed to retrieve events for event type {}", eventType, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Converts EventEntity to EventReplayData for API response.
     */
    private EventReplayData convertToReplayData(EventEntity event) {
        return new EventReplayData(
            event.getId(),
            event.getAggregateId(),
            event.getAggregateType(),
            event.getSequenceNumber(),
            event.getEventType(),
            event.getEventData(),
            event.getMetadata(),
            event.getTimestamp()
        );
    }

    /**
     * Data class for event replay information.
     */
    public static class EventReplayData {
        private final Long id;
        private final String aggregateId;
        private final String aggregateType;
        private final Long sequenceNumber;
        private final String eventType;
        private final Object eventData;
        private final Object metadata;
        private final OffsetDateTime timestamp;

        public EventReplayData(Long id, String aggregateId, String aggregateType, 
                             Long sequenceNumber, String eventType, Object eventData, 
                             Object metadata, OffsetDateTime timestamp) {
            this.id = id;
            this.aggregateId = aggregateId;
            this.aggregateType = aggregateType;
            this.sequenceNumber = sequenceNumber;
            this.eventType = eventType;
            this.eventData = eventData;
            this.metadata = metadata;
            this.timestamp = timestamp;
        }

        // Getters
        public Long getId() { return id; }
        public String getAggregateId() { return aggregateId; }
        public String getAggregateType() { return aggregateType; }
        public Long getSequenceNumber() { return sequenceNumber; }
        public String getEventType() { return eventType; }
        public Object getEventData() { return eventData; }
        public Object getMetadata() { return metadata; }
        public OffsetDateTime getTimestamp() { return timestamp; }
    }
}