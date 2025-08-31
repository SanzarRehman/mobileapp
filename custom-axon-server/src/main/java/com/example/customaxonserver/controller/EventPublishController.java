//package com.example.customaxonserver.controller;
//
//import com.example.customaxonserver.service.EventStoreService;
//import com.example.customaxonserver.service.KafkaEventPublisher;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.http.HttpStatus;
//import org.springframework.http.ResponseEntity;
//import org.springframework.web.bind.annotation.*;
//
//import java.util.Map;
//
///**
// * REST controller for receiving events from external applications.
// * This endpoint allows the main application to publish events to the custom server.
// */
//@RestController
//@RequestMapping("/api/events")
//public class EventPublishController {
//
//    private static final Logger logger = LoggerFactory.getLogger(EventPublishController.class);
//
//    private final EventStoreService eventStoreService;
//    private final KafkaEventPublisher kafkaEventPublisher;
//
//    @Autowired
//    public EventPublishController(EventStoreService eventStoreService,
//                                KafkaEventPublisher kafkaEventPublisher) {
//        this.eventStoreService = eventStoreService;
//        this.kafkaEventPublisher = kafkaEventPublisher;
//    }
//
//    /**
//     * Publishes an event to the custom server for storage and distribution.
//     * This endpoint receives events from the main application's EventPublishingInterceptor.
//     *
//     * @param eventPayload The event data from the main application
//     * @return Response indicating success or failure
//     */
//    @PostMapping("/publish")
//    public ResponseEntity<Map<String, Object>> publishEvent(@RequestBody Map<String, Object> eventPayload) {
//        try {
//            logger.info("Received event for publication: {}", eventPayload.get("eventType"));
//
//            // Extract event information
//            String aggregateId = (String) eventPayload.get("aggregateId");
//            String aggregateType = (String) eventPayload.get("aggregateType");
//            String eventType = (String) eventPayload.get("eventType");
//            Object payload = eventPayload.get("payload");
//            Object metadata = eventPayload.get("metadata");
//
//            // Validate required fields
//            if (aggregateId == null || aggregateType == null || eventType == null) {
//                logger.error("Missing required fields in event payload: aggregateId={}, aggregateType={}, eventType={}",
//                           aggregateId, aggregateType, eventType);
//                return ResponseEntity.badRequest().body(Map.of(
//                    "success", false,
//                    "message", "Missing required fields: aggregateId, aggregateType, or eventType"
//                ));
//            }
//
//            // Determine expected sequence number
//            Long clientSequence = eventPayload.get("sequenceNumber") != null ?
//                ((Number) eventPayload.get("sequenceNumber")).longValue() : null;
//            Long nextSequence = eventStoreService.getNextSequenceNumber(aggregateId);
//            Long sequenceNumber;
//
//            // If client supplied a sequence, validate it matches the server's next
//            if (clientSequence != null) {
//                if (!clientSequence.equals(nextSequence)) {
//                    logger.warn("Sequence mismatch for aggregate {}. Client: {}, Server Next: {}",
//                                aggregateId, clientSequence, nextSequence);
//                    return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
//                        "success", false,
//                        "message", "Sequence conflict: provided sequenceNumber does not match server's next",
//                        "providedSequence", clientSequence,
//                        "nextSequence", nextSequence,
//                        "aggregateId", aggregateId
//                    ));
//                }
//                sequenceNumber = clientSequence;
//            } else {
//                sequenceNumber = nextSequence;
//            }
//
//            // Store event in the event store
//            var storedEvent = eventStoreService.storeEvent(
//                aggregateId,
//                aggregateType,
//                sequenceNumber,
//                eventType,
//                payload,
//                metadata
//            );
//
//            // Publish to Kafka for distribution
//            kafkaEventPublisher.publishEvent(storedEvent);
//
//            logger.info("Successfully stored and published event {} for aggregate {}",
//                       eventType, aggregateId);
//
//            return ResponseEntity.ok(Map.of(
//                "success", true,
//                "message", "Event published successfully",
//                "eventId", storedEvent.getId(),
//                "aggregateId", aggregateId,
//                "sequenceNumber", sequenceNumber
//            ));
//
//        } catch (EventStoreService.ConcurrencyException e) {
//            logger.error("Concurrency conflict while storing event: {}", e.getMessage());
//            Long nextSequence = eventStoreService.getNextSequenceNumber((String) eventPayload.get("aggregateId"));
//            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
//                "success", false,
//                "message", "Concurrency conflict: " + e.getMessage(),
//                "nextSequence", nextSequence
//            ));
//
//        } catch (Exception e) {
//            logger.error("Failed to publish event: {}", e.getMessage(), e);
//            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
//                "success", false,
//                "message", "Failed to publish event: " + e.getMessage()
//            ));
//        }
//    }
//}
