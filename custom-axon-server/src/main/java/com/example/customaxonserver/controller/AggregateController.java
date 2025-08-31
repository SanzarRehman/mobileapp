package com.example.customaxonserver.controller;

import com.example.customaxonserver.entity.EventEntity;
import com.example.customaxonserver.service.EventStoreService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST controller for retrieving events for aggregate replay.
 * Used by the main application to reconstruct aggregate state.
 */
@RestController
@RequestMapping("/api/aggregates")
public class AggregateController {

    private static final Logger logger = LoggerFactory.getLogger(AggregateController.class);

    private final EventStoreService eventStoreService;

    @Autowired
    public AggregateController(EventStoreService eventStoreService) {
        this.eventStoreService = eventStoreService;
    }

    /**
     * Retrieves all events for an aggregate for replay purposes.
     */
    @GetMapping("/{aggregateId}/events")
    public ResponseEntity<Map<String, Object>> getEventsForAggregate(
            @PathVariable String aggregateId,
            @RequestParam(required = false) Long fromSequence) {
        
        logger.info("Retrieving events for aggregate: {} from sequence: {}", aggregateId, fromSequence);
        
        try {
            List<EventEntity> events;
            if (fromSequence != null) {
                events = eventStoreService.getEventsForAggregate(aggregateId, fromSequence);
            } else {
                events = eventStoreService.getEventsForAggregate(aggregateId);
            }
            
            Long currentSequence = eventStoreService.getCurrentSequenceNumber(aggregateId);
            
            return ResponseEntity.ok(Map.of(
                "aggregateId", aggregateId,
                "currentSequence", currentSequence,
                "eventCount", events.size(),
                "events", events
            ));
            
        } catch (Exception e) {
            logger.error("Failed to retrieve events for aggregate: {}", aggregateId, e);
            return ResponseEntity.status(500).body(Map.of(
                "error", "Failed to retrieve events",
                "message", e.getMessage(),
                "aggregateId", aggregateId
            ));
        }
    }

    /**
     * Gets the current sequence number for an aggregate.
     */
    @GetMapping("/{aggregateId}/sequence")
    public ResponseEntity<Map<String, Object>> getCurrentSequence(@PathVariable String aggregateId) {
        try {
            Long currentSequence = eventStoreService.getCurrentSequenceNumber(aggregateId);
            Long nextSequence = eventStoreService.getNextSequenceNumber(aggregateId);
            boolean hasEvents = eventStoreService.hasEvents(aggregateId);
            
            return ResponseEntity.ok(Map.of(
                "aggregateId", aggregateId,
                "currentSequence", currentSequence,
                "nextSequence", nextSequence,
                "hasEvents", hasEvents
            ));
            
        } catch (Exception e) {
            logger.error("Failed to get sequence for aggregate: {}", aggregateId, e);
            return ResponseEntity.status(500).body(Map.of(
                "error", "Failed to get sequence",
                "message", e.getMessage(),
                "aggregateId", aggregateId
            ));
        }
    }
}