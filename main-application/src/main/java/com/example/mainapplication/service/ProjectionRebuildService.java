package com.example.mainapplication.service;

import com.example.mainapplication.handler.UserEventHandler;
import com.example.mainapplication.projection.UserProjection;
import com.example.mainapplication.repository.UserProjectionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Service for rebuilding projections by replaying events from the event store.
 * Provides functionality to rebuild specific projections or all projections
 * with progress tracking and status reporting.
 */
@Service
public class ProjectionRebuildService {

    private static final Logger logger = LoggerFactory.getLogger(ProjectionRebuildService.class);

    private final UserProjectionRepository userProjectionRepository;
    private final UserEventHandler userEventHandler;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${custom-axon-server.base-url:http://localhost:8081}")
    private String customAxonServerBaseUrl;

    // Track rebuild progress and status
    private final Map<String, RebuildStatus> rebuildStatuses = new ConcurrentHashMap<>();

    @Autowired
    public ProjectionRebuildService(UserProjectionRepository userProjectionRepository,
                                  UserEventHandler userEventHandler,
                                  RestTemplate restTemplate,
                                  ObjectMapper objectMapper) {
        this.userProjectionRepository = userProjectionRepository;
        this.userEventHandler = userEventHandler;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Initiates a rebuild of all user projections.
     * This method clears existing projections and replays all events.
     *
     * @return Rebuild ID for tracking progress
     */
    @Async("projectionRebuildExecutor")
    public CompletableFuture<String> rebuildAllUserProjections() {
        String rebuildId = generateRebuildId("user-projections");
        logger.info("Starting rebuild of all user projections with ID: {}", rebuildId);

        try {
            RebuildStatus status = new RebuildStatus(rebuildId, "user-projections", 
                                                   RebuildStatus.Status.IN_PROGRESS);
            rebuildStatuses.put(rebuildId, status);

            // Clear existing projections
            logger.info("Clearing existing user projections");
            userProjectionRepository.deleteAll();
            status.setPhase("CLEARING_PROJECTIONS");

            // Get all events from custom server
            String eventsUrl = customAxonServerBaseUrl + "/api/events/replay/all";
            ResponseEntity<JsonNode> response = restTemplate.getForEntity(eventsUrl, JsonNode.class);
            
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                status.setStatus(RebuildStatus.Status.FAILED);
                status.setErrorMessage("Failed to retrieve events from custom server");
                status.setCompletedAt(OffsetDateTime.now());
                return CompletableFuture.completedFuture(rebuildId);
            }

            JsonNode eventsData = response.getBody();
            if (!eventsData.isArray()) {
                throw new ProjectionRebuildException("Invalid events data format received");
            }

            status.setTotalEvents(eventsData.size());
            status.setPhase("REPLAYING_EVENTS");
            logger.info("Retrieved {} events for replay", eventsData.size());

            // Process events in chronological order
            AtomicInteger processedCount = new AtomicInteger(0);
            for (JsonNode eventNode : eventsData) {
                try {
                    processEventForRebuild(eventNode);
                    int processed = processedCount.incrementAndGet();
                    status.setProcessedEvents(processed);
                    
                    if (processed % 100 == 0) {
                        logger.info("Processed {} of {} events for rebuild {}", 
                                  processed, status.getTotalEvents(), rebuildId);
                    }
                } catch (Exception e) {
                    logger.error("Error processing event during rebuild: {}", eventNode, e);
                    status.incrementErrorCount();
                }
            }

            status.setStatus(RebuildStatus.Status.COMPLETED);
            status.setCompletedAt(OffsetDateTime.now());
            logger.info("Completed rebuild {} - processed {} events with {} errors", 
                       rebuildId, status.getProcessedEvents(), status.getErrorCount());

        } catch (Exception e) {
            logger.error("Failed to rebuild user projections", e);
            RebuildStatus status = rebuildStatuses.get(rebuildId);
            if (status != null) {
                status.setStatus(RebuildStatus.Status.FAILED);
                status.setErrorMessage(e.getMessage());
                status.setCompletedAt(OffsetDateTime.now());
            }
            throw new ProjectionRebuildException("Rebuild failed: " + e.getMessage(), e);
        }

        return CompletableFuture.completedFuture(rebuildId);
    }

    /**
     * Rebuilds projections for a specific aggregate.
     *
     * @param aggregateId The aggregate ID to rebuild projections for
     * @return Rebuild ID for tracking progress
     */
    @Async("projectionRebuildExecutor")
    public CompletableFuture<String> rebuildProjectionsForAggregate(String aggregateId) {
        String rebuildId = generateRebuildId("aggregate-" + aggregateId);
        logger.info("Starting rebuild for aggregate {} with ID: {}", aggregateId, rebuildId);

        try {
            RebuildStatus status = new RebuildStatus(rebuildId, "aggregate-" + aggregateId, 
                                                   RebuildStatus.Status.IN_PROGRESS);
            rebuildStatuses.put(rebuildId, status);

            // Clear existing projection for this aggregate
            logger.info("Clearing existing projection for aggregate {}", aggregateId);
            userProjectionRepository.deleteById(aggregateId);
            status.setPhase("CLEARING_PROJECTIONS");

            // Get events for specific aggregate
            String eventsUrl = customAxonServerBaseUrl + "/api/events/aggregate/" + aggregateId;
            ResponseEntity<JsonNode> response = restTemplate.getForEntity(eventsUrl, JsonNode.class);
            
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                status.setStatus(RebuildStatus.Status.FAILED);
                status.setErrorMessage("Failed to retrieve events for aggregate " + aggregateId);
                status.setCompletedAt(OffsetDateTime.now());
                return CompletableFuture.completedFuture(rebuildId);
            }

            JsonNode eventsData = response.getBody();
            if (!eventsData.isArray()) {
                throw new ProjectionRebuildException("Invalid events data format received");
            }

            status.setTotalEvents(eventsData.size());
            status.setPhase("REPLAYING_EVENTS");
            logger.info("Retrieved {} events for aggregate {} replay", eventsData.size(), aggregateId);

            // Process events in sequence order
            AtomicInteger processedCount = new AtomicInteger(0);
            for (JsonNode eventNode : eventsData) {
                try {
                    processEventForRebuild(eventNode);
                    status.setProcessedEvents(processedCount.incrementAndGet());
                } catch (Exception e) {
                    logger.error("Error processing event for aggregate {} during rebuild: {}", 
                               aggregateId, eventNode, e);
                    status.incrementErrorCount();
                }
            }

            status.setStatus(RebuildStatus.Status.COMPLETED);
            status.setCompletedAt(OffsetDateTime.now());
            logger.info("Completed rebuild {} for aggregate {} - processed {} events with {} errors", 
                       rebuildId, aggregateId, status.getProcessedEvents(), status.getErrorCount());

        } catch (Exception e) {
            logger.error("Failed to rebuild projections for aggregate {}", aggregateId, e);
            RebuildStatus status = rebuildStatuses.get(rebuildId);
            if (status != null) {
                status.setStatus(RebuildStatus.Status.FAILED);
                status.setErrorMessage(e.getMessage());
                status.setCompletedAt(OffsetDateTime.now());
            }
            throw new ProjectionRebuildException("Rebuild failed for aggregate " + aggregateId + ": " + e.getMessage(), e);
        }

        return CompletableFuture.completedFuture(rebuildId);
    }

    /**
     * Gets the status of a rebuild operation.
     *
     * @param rebuildId The rebuild ID
     * @return Rebuild status or null if not found
     */
    public RebuildStatus getRebuildStatus(String rebuildId) {
        return rebuildStatuses.get(rebuildId);
    }

    /**
     * Gets all active rebuild statuses.
     *
     * @return Map of rebuild ID to status
     */
    public Map<String, RebuildStatus> getAllRebuildStatuses() {
        return Map.copyOf(rebuildStatuses);
    }

    /**
     * Cancels a running rebuild operation.
     *
     * @param rebuildId The rebuild ID to cancel
     * @return true if cancelled, false if not found or already completed
     */
    public boolean cancelRebuild(String rebuildId) {
        RebuildStatus status = rebuildStatuses.get(rebuildId);
        if (status != null && status.getStatus() == RebuildStatus.Status.IN_PROGRESS) {
            status.setStatus(RebuildStatus.Status.CANCELLED);
            status.setCompletedAt(OffsetDateTime.now());
            logger.info("Cancelled rebuild {}", rebuildId);
            return true;
        }
        return false;
    }

    /**
     * Processes a single event during rebuild by delegating to appropriate event handler.
     */
    @Transactional
    private void processEventForRebuild(JsonNode eventNode) throws Exception {
        String eventType = eventNode.get("eventType").asText();
        JsonNode eventData = eventNode.get("eventData");
        
        logger.debug("Processing event of type {} for rebuild", eventType);

        switch (eventType) {
            case "UserCreatedEvent":
                processUserCreatedEvent(eventData);
                break;
            case "UserUpdatedEvent":
                processUserUpdatedEvent(eventData);
                break;
            default:
                logger.debug("Skipping unknown event type: {}", eventType);
        }
    }

    /**
     * Processes UserCreatedEvent during rebuild.
     */
    private void processUserCreatedEvent(JsonNode eventData) throws Exception {
        String userId = eventData.get("userId").asText();
        String fullName = eventData.get("fullName").asText();
        String email = eventData.get("email").asText();
        OffsetDateTime createdAt = OffsetDateTime.parse(eventData.get("createdAt").asText());

        // Create projection directly (bypass event handler to avoid duplicate processing)
        if (!userProjectionRepository.existsById(userId)) {
            UserProjection projection = new UserProjection();
            projection.setId(userId);
            projection.setName(fullName);
            projection.setEmail(email);
            projection.setStatus("ACTIVE");
            projection.setCreatedAt(createdAt);
            projection.setUpdatedAt(createdAt);

            userProjectionRepository.save(projection);
            logger.debug("Created user projection for user: {}", userId);
        }
    }

    /**
     * Processes UserUpdatedEvent during rebuild.
     */
    private void processUserUpdatedEvent(JsonNode eventData) throws Exception {
        String userId = eventData.get("userId").asText();
        String fullName = eventData.get("fullName").asText();
        String email = eventData.get("email").asText();
        OffsetDateTime updatedAt = OffsetDateTime.parse(eventData.get("updatedAt").asText());

        // Update existing projection or create if it doesn't exist (for rebuild scenarios)
        UserProjection projection = userProjectionRepository.findById(userId).orElse(null);
        if (projection != null) {
            projection.setName(fullName);
            projection.setEmail(email);
            projection.setUpdatedAt(updatedAt);

            userProjectionRepository.save(projection);
            logger.debug("Updated user projection for user: {}", userId);
        } else {
            // During rebuild, we might encounter update events without create events
            // Create a new projection with the update data
            projection = new UserProjection();
            projection.setId(userId);
            projection.setName(fullName);
            projection.setEmail(email);
            projection.setStatus("ACTIVE"); // Default status
            projection.setCreatedAt(updatedAt); // Use update time as creation time
            projection.setUpdatedAt(updatedAt);

            userProjectionRepository.save(projection);
            logger.debug("Created user projection from update event for user: {}", userId);
        }
    }

    /**
     * Generates a unique rebuild ID.
     */
    private String generateRebuildId(String prefix) {
        return prefix + "-" + System.currentTimeMillis() + "-" + 
               Thread.currentThread().getId();
    }

    /**
     * Status tracking class for rebuild operations.
     */
    public static class RebuildStatus {
        public enum Status {
            IN_PROGRESS, COMPLETED, FAILED, CANCELLED
        }

        private final String rebuildId;
        private final String projectionType;
        private final OffsetDateTime startedAt;
        private Status status;
        private String phase;
        private int totalEvents;
        private final AtomicInteger processedEvents = new AtomicInteger(0);
        private final AtomicInteger errorCount = new AtomicInteger(0);
        private String errorMessage;
        private OffsetDateTime completedAt;

        public RebuildStatus(String rebuildId, String projectionType, Status status) {
            this.rebuildId = rebuildId;
            this.projectionType = projectionType;
            this.status = status;
            this.startedAt = OffsetDateTime.now();
            this.phase = "INITIALIZING";
        }

        // Getters and setters
        public String getRebuildId() { return rebuildId; }
        public String getProjectionType() { return projectionType; }
        public OffsetDateTime getStartedAt() { return startedAt; }
        public Status getStatus() { return status; }
        public void setStatus(Status status) { this.status = status; }
        public String getPhase() { return phase; }
        public void setPhase(String phase) { this.phase = phase; }
        public int getTotalEvents() { return totalEvents; }
        public void setTotalEvents(int totalEvents) { this.totalEvents = totalEvents; }
        public int getProcessedEvents() { return processedEvents.get(); }
        public void setProcessedEvents(int processedEvents) { this.processedEvents.set(processedEvents); }
        public int getErrorCount() { return errorCount.get(); }
        public void incrementErrorCount() { this.errorCount.incrementAndGet(); }
        public String getErrorMessage() { return errorMessage; }
        public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
        public OffsetDateTime getCompletedAt() { return completedAt; }
        public void setCompletedAt(OffsetDateTime completedAt) { this.completedAt = completedAt; }

        public double getProgressPercentage() {
            return totalEvents > 0 ? (double) processedEvents.get() / totalEvents * 100 : 0;
        }
    }

    /**
     * Exception thrown when projection rebuild operations fail.
     */
    public static class ProjectionRebuildException extends RuntimeException {
        public ProjectionRebuildException(String message) {
            super(message);
        }

        public ProjectionRebuildException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}