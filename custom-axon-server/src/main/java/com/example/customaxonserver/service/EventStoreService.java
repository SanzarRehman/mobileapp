package com.example.customaxonserver.service;

import com.example.customaxonserver.entity.EventEntity;
import com.example.customaxonserver.entity.SnapshotEntity;
import com.example.customaxonserver.exception.EventStoreException;
import com.example.customaxonserver.messaging.DeadLetterQueueHandler;
import com.example.customaxonserver.repository.EventRepository;
import com.example.customaxonserver.resilience.CircuitBreakerService;
import com.example.customaxonserver.resilience.RetryService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Service for managing event store operations.
 * Provides methods for storing and retrieving events with proper sequencing
 * and concurrency control using optimistic locking.
 */
@Service
@Transactional
public class EventStoreService {

    private static final Logger logger = LoggerFactory.getLogger(EventStoreService.class);

    private final EventRepository eventRepository;
    private final ObjectMapper objectMapper;
    private final CircuitBreakerService circuitBreakerService;
    private final RetryService retryService;
    private final DeadLetterQueueHandler deadLetterQueueHandler;
    private final ConcurrencyControlService concurrencyControlService;
    private SnapshotService snapshotService;

    @Autowired
    public EventStoreService(EventRepository eventRepository, 
                           ObjectMapper objectMapper,
                           CircuitBreakerService circuitBreakerService,
                           RetryService retryService,
                           DeadLetterQueueHandler deadLetterQueueHandler,
                           ConcurrencyControlService concurrencyControlService) {
        this.eventRepository = eventRepository;
        this.objectMapper = objectMapper;
        this.circuitBreakerService = circuitBreakerService;
        this.retryService = retryService;
        this.deadLetterQueueHandler = deadLetterQueueHandler;
        this.concurrencyControlService = concurrencyControlService;
    }

    /**
     * Sets the SnapshotService dependency. This is done via setter injection to avoid circular dependency
     * since SnapshotService depends on EventStoreService.
     */
    @Autowired
    public void setSnapshotService(SnapshotService snapshotService) {
        this.snapshotService = snapshotService;
    }

    /**
     * Stores a single event in the event store.
     * Implements optimistic locking by checking sequence number uniqueness.
     *
     * @param aggregateId The unique identifier of the aggregate
     * @param aggregateType The type of the aggregate
     * @param expectedSequenceNumber The expected sequence number for concurrency control
     * @param eventType The type of the event
     * @param eventData The event payload
     * @param metadata Additional metadata for the event
     * @return The stored EventEntity
     * @throws ConcurrencyException if the expected sequence number doesn't match
     * @throws EventStoreException if storage fails
     */
    public EventEntity storeEvent(String aggregateId, String aggregateType, 
                                 Long expectedSequenceNumber, String eventType, 
                                 Object eventData, Object metadata) {
        
        logger.debug("Storing event for aggregate {} with sequence {}", aggregateId, expectedSequenceNumber);
        
        return concurrencyControlService.executeWithFullConcurrencyControl(aggregateId, () -> {
            try {
                // Verify expected sequence number for optimistic locking
                long currentSequenceNumber = getCurrentSequenceNumber(aggregateId);
                long expected = expectedSequenceNumber;
                boolean validFirstInsert = (currentSequenceNumber == 0 && expected == 0);
                boolean validNext = (expected == currentSequenceNumber + 1);
                if (!(validFirstInsert || validNext)) {
                    throw new ConcurrencyException(
                        String.format("Expected sequence number %d (or 0 on first insert) but current is %d for aggregate %s", 
                                    expectedSequenceNumber, currentSequenceNumber, aggregateId));
                }

                // Convert event data and metadata to JsonNode
                JsonNode eventDataNode = objectMapper.valueToTree(eventData);
                JsonNode metadataNode = metadata != null ? objectMapper.valueToTree(metadata) : null;

                // Create and save event entity
                EventEntity eventEntity = new EventEntity(aggregateId, aggregateType, 
                                                        expectedSequenceNumber, eventType, eventDataNode);
                eventEntity.setMetadata(metadataNode);
                
                EventEntity savedEvent = eventRepository.save(eventEntity);
                
                logger.info("Successfully stored event {} for aggregate {} with sequence {}", 
                           eventType, aggregateId, expectedSequenceNumber);
                
                return savedEvent;
                
            } catch (ConcurrencyException e) {
                // Re-throw concurrency exceptions as-is
                throw e;
            } catch (DataIntegrityViolationException e) {
                logger.error("Concurrency violation when storing event for aggregate {}", aggregateId, e);
                throw new ConcurrencyException("Concurrent modification detected for aggregate " + aggregateId, e);
            } catch (Exception e) {
                logger.error("Failed to store event for aggregate {}", aggregateId, e);
                throw new EventStoreException("Failed to store event for aggregate " + aggregateId, e);
            }
        });
    }

    /**
     * Stores multiple events atomically for the same aggregate.
     * All events must be for the same aggregate and have consecutive sequence numbers.
     *
     * @param aggregateId The unique identifier of the aggregate
     * @param aggregateType The type of the aggregate
     * @param startingSequenceNumber The starting sequence number
     * @param events List of event data objects
     * @return List of stored EventEntity objects
     * @throws ConcurrencyException if sequence numbers conflict
     * @throws EventStoreException if storage fails
     */
    public List<EventEntity> storeEvents(String aggregateId, String aggregateType, 
                                       Long startingSequenceNumber, List<EventData> events) {
        
        logger.debug("Storing {} events for aggregate {} starting at sequence {}", 
                    events.size(), aggregateId, startingSequenceNumber);
        
        return concurrencyControlService.executeWithFullConcurrencyControl(aggregateId, () -> {
            try {
                // Verify expected sequence number for optimistic locking
                long currentSequenceNumber = getCurrentSequenceNumber(aggregateId);
                long starting = startingSequenceNumber;
                boolean validFirstInsert = (currentSequenceNumber == 0 && starting == 0);
                boolean validNext = (starting == currentSequenceNumber + 1);
                if (!(validFirstInsert || validNext)) {
                    throw new ConcurrencyException(
                        String.format("Expected starting sequence number %d (or 0 on first insert) but current is %d for aggregate %s", 
                                    startingSequenceNumber, currentSequenceNumber, aggregateId));
                }

                List<EventEntity> eventEntities = new java.util.ArrayList<>();
                Long sequenceNumber = startingSequenceNumber;
                
                for (EventData eventData : events) {
                    JsonNode eventDataNode = objectMapper.valueToTree(eventData.getPayload());
                    JsonNode metadataNode = eventData.getMetadata() != null ? 
                                          objectMapper.valueToTree(eventData.getMetadata()) : null;

                    EventEntity eventEntity = new EventEntity(aggregateId, aggregateType, 
                                                            sequenceNumber, eventData.getEventType(), eventDataNode);
                    eventEntity.setMetadata(metadataNode);
                    eventEntities.add(eventEntity);
                    sequenceNumber++;
                }
                
                List<EventEntity> savedEvents = eventRepository.saveAll(eventEntities);
                
                logger.info("Successfully stored {} events for aggregate {} starting at sequence {}", 
                           events.size(), aggregateId, startingSequenceNumber);
                
                return savedEvents;
                
            } catch (ConcurrencyException e) {
                // Re-throw concurrency exceptions as-is
                throw e;
            } catch (DataIntegrityViolationException e) {
                logger.error("Concurrency violation when storing events for aggregate {}", aggregateId, e);
                throw new ConcurrencyException("Concurrent modification detected for aggregate " + aggregateId, e);
            } catch (Exception e) {
                logger.error("Failed to store events for aggregate {}", aggregateId, e);
                throw new EventStoreException("Failed to store events for aggregate " + aggregateId, e);
            }
        });
    }

    /**
     * Retrieves all events for a specific aggregate in sequence order.
     *
     * @param aggregateId The unique identifier of the aggregate
     * @return List of events ordered by sequence number
     */
    @Transactional(readOnly = true)
    public List<EventEntity> getEventsForAggregate(String aggregateId) {
        logger.debug("Retrieving all events for aggregate {}", aggregateId);
        
        return concurrencyControlService.executeWithReadLock(aggregateId, () -> {
            List<EventEntity> events = eventRepository.findByAggregateIdOrderBySequenceNumber(aggregateId);
            logger.debug("Retrieved {} events for aggregate {}", events.size(), aggregateId);
            return events;
        });
    }

    /**
     * Retrieves events for a specific aggregate starting from a given sequence number.
     *
     * @param aggregateId The unique identifier of the aggregate
     * @param fromSequenceNumber The starting sequence number (inclusive)
     * @return List of events from the specified sequence number
     */
    @Transactional(readOnly = true)
    public List<EventEntity> getEventsForAggregate(String aggregateId, Long fromSequenceNumber) {
        logger.debug("Retrieving events for aggregate {} from sequence {}", aggregateId, fromSequenceNumber);
        
        List<EventEntity> events = eventRepository
            .findByAggregateIdAndSequenceNumberGreaterThanEqualOrderBySequenceNumber(aggregateId, fromSequenceNumber);
        
        logger.debug("Retrieved {} events for aggregate {} from sequence {}", 
                    events.size(), aggregateId, fromSequenceNumber);
        return events;
    }

    /**
     * Gets the current sequence number for an aggregate.
     *
     * @param aggregateId The unique identifier of the aggregate
     * @return The current sequence number (0 if no events exist)
     */
    @Transactional(readOnly = true)
    public Long getCurrentSequenceNumber(String aggregateId) {
        Optional<EventEntity> latestEvent = eventRepository
            .findTopByAggregateIdOrderBySequenceNumberDesc(aggregateId);
        
        return latestEvent.map(EventEntity::getSequenceNumber).orElse(0L);
    }

    /**
     * Gets the next sequence number for an aggregate.
     *
     * @param aggregateId The unique identifier of the aggregate
     * @return The next sequence number to use
     */
    @Transactional(readOnly = true)
    public Long getNextSequenceNumber(String aggregateId) {
        return 0L;
        //return getCurrentSequenceNumber(aggregateId) + 1;
    }

    /**
     * Checks if events exist for a specific aggregate.
     *
     * @param aggregateId The unique identifier of the aggregate
     * @return true if events exist, false otherwise
     */
    @Transactional(readOnly = true)
    public boolean hasEvents(String aggregateId) {
        return eventRepository.countByAggregateId(aggregateId) > 0;
    }

    /**
     * Retrieves events by aggregate type within a time range.
     *
     * @param aggregateType The type of aggregate
     * @param from Start time (inclusive)
     * @param to End time (inclusive)
     * @return List of events within the time range
     */
    @Transactional(readOnly = true)
    public List<EventEntity> getEventsByAggregateType(String aggregateType, 
                                                     OffsetDateTime from, OffsetDateTime to) {
        logger.debug("Retrieving events for aggregate type {} between {} and {}", 
                    aggregateType, from, to);
        
        List<EventEntity> events = eventRepository
            .findByAggregateTypeAndTimestampBetweenOrderByTimestamp(aggregateType, from, to);
        
        logger.debug("Retrieved {} events for aggregate type {} in time range", 
                    events.size(), aggregateType);
        return events;
    }

    /**
     * Retrieves events by event type within a time range.
     *
     * @param eventType The type of event
     * @param from Start time (inclusive)
     * @param to End time (inclusive)
     * @return List of events within the time range
     */
    @Transactional(readOnly = true)
    public List<EventEntity> getEventsByEventType(String eventType, 
                                                 OffsetDateTime from, OffsetDateTime to) {
        logger.debug("Retrieving events of type {} between {} and {}", eventType, from, to);
        
        List<EventEntity> events = eventRepository
            .findByEventTypeAndTimestampBetweenOrderByTimestamp(eventType, from, to);
        
        logger.debug("Retrieved {} events of type {} in time range", events.size(), eventType);
        return events;
    }

    /**
     * Retrieves events after a specific timestamp for event replay.
     *
     * @param timestamp The timestamp to start from
     * @return List of events after the specified timestamp
     */
    @Transactional(readOnly = true)
    public List<EventEntity> getEventsAfterTimestamp(OffsetDateTime timestamp) {
        logger.debug("Retrieving events after timestamp {}", timestamp);
        
        List<EventEntity> events = eventRepository.findByTimestampGreaterThanOrderByTimestamp(timestamp);
        
        logger.debug("Retrieved {} events after timestamp {}", events.size(), timestamp);
        return events;
    }

    /**
     * Retrieves events for aggregate replay optimized with snapshot loading.
     * This method uses snapshots to reduce the number of events that need to be loaded.
     *
     * @param aggregateId The unique identifier of the aggregate
     * @return EventReplayData containing snapshot and events for efficient replay
     */
    @Transactional(readOnly = true)
    public EventReplayData getEventsForReplayWithSnapshot(String aggregateId) {
        logger.debug("Retrieving events for replay with snapshot optimization for aggregate {}", aggregateId);
        
        if (snapshotService != null) {
            SnapshotService.SnapshotReplayData replayData = snapshotService.getEventsForReplay(aggregateId);
            
            logger.debug("Retrieved {} events for replay for aggregate {} (with snapshot: {})", 
                        replayData.getEvents().size(), aggregateId, replayData.hasSnapshot());
            
            return new EventReplayData(replayData.getSnapshot().orElse(null), replayData.getEvents());
        } else {
            // Fallback to regular event loading if snapshot service is not available
            logger.debug("SnapshotService not available, falling back to regular event loading");
            List<EventEntity> events = getEventsForAggregate(aggregateId);
            return new EventReplayData(null, events);
        }
    }

    /**
     * Data class containing snapshot and events for optimized aggregate replay.
     */
    public static class EventReplayData {
        private final SnapshotEntity snapshot;
        private final List<EventEntity> events;

        public EventReplayData(SnapshotEntity snapshot, List<EventEntity> events) {
            this.snapshot = snapshot;
            this.events = events;
        }

        public Optional<SnapshotEntity> getSnapshot() {
            return Optional.ofNullable(snapshot);
        }

        public List<EventEntity> getEvents() {
            return events;
        }

        public boolean hasSnapshot() {
            return snapshot != null;
        }

        public Long getStartingSequenceNumber() {
            return hasSnapshot() ? snapshot.getSequenceNumber() : 0L;
        }
    }

    /**
     * Data class for event information when storing multiple events.
     */
    public static class EventData {
        private final String eventType;
        private final Object payload;
        private final Object metadata;

        public EventData(String eventType, Object payload, Object metadata) {
            this.eventType = eventType;
            this.payload = payload;
            this.metadata = metadata;
        }

        public EventData(String eventType, Object payload) {
            this(eventType, payload, null);
        }

        public String getEventType() {
            return eventType;
        }

        public Object getPayload() {
            return payload;
        }

        public Object getMetadata() {
            return metadata;
        }
    }

    /**
     * Exception thrown when concurrency conflicts occur.
     */
    public static class ConcurrencyException extends RuntimeException {
        public ConcurrencyException(String message) {
            super(message);
        }

        public ConcurrencyException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Exception thrown when event store operations fail.
     */
    public static class EventStoreException extends RuntimeException {
        public EventStoreException(String message) {
            super(message);
        }

        public EventStoreException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}