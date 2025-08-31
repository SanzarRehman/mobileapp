package com.example.mainapplication.controller;

import com.example.mainapplication.ProjectionRebuildService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * REST controller for managing projection rebuild operations.
 * Provides endpoints for triggering rebuilds, checking status, and managing rebuild operations.
 */
@RestController
@RequestMapping("/api/projections/rebuild")
public class ProjectionRebuildController {

    private static final Logger logger = LoggerFactory.getLogger(ProjectionRebuildController.class);

    private final ProjectionRebuildService projectionRebuildService;

    @Autowired
    public ProjectionRebuildController(ProjectionRebuildService projectionRebuildService) {
        this.projectionRebuildService = projectionRebuildService;
    }

    /**
     * Triggers a rebuild of all user projections.
     * This operation runs asynchronously and returns immediately with a rebuild ID.
     *
     * @return Response containing the rebuild ID for tracking progress
     */
    @PostMapping("/all")
    public ResponseEntity<RebuildResponse> rebuildAllProjections() {
        logger.info("Received request to rebuild all user projections");
        
        try {
            CompletableFuture<String> rebuildFuture = projectionRebuildService.rebuildAllUserProjections();
            String rebuildId = rebuildFuture.get(); // This will complete immediately since we return the ID synchronously
            
            RebuildResponse response = new RebuildResponse(rebuildId, 
                "Rebuild of all user projections started successfully");
            
            logger.info("Started rebuild of all projections with ID: {}", rebuildId);
            return ResponseEntity.accepted().body(response);
            
        } catch (Exception e) {
            logger.error("Failed to start rebuild of all projections", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new RebuildResponse(null, "Failed to start rebuild: " + e.getMessage()));
        }
    }

    /**
     * Triggers a rebuild of projections for a specific aggregate.
     *
     * @param aggregateId The ID of the aggregate to rebuild projections for
     * @return Response containing the rebuild ID for tracking progress
     */
    @PostMapping({"/aggregate/{aggregateId}", "/aggregate/"})
    public ResponseEntity<RebuildResponse> rebuildProjectionsForAggregate(
            @PathVariable(value = "aggregateId", required = false) String aggregateId) {
        logger.info("Received request to rebuild projections for aggregate: {}", aggregateId);
        
        if (aggregateId == null || aggregateId.trim().isEmpty()) {
            return ResponseEntity.badRequest()
                .body(new RebuildResponse(null, "Aggregate ID cannot be empty"));
        }
        
        try {
            CompletableFuture<String> rebuildFuture = projectionRebuildService
                .rebuildProjectionsForAggregate(aggregateId);
            String rebuildId = rebuildFuture.get(); // This will complete immediately since we return the ID synchronously
            
            RebuildResponse response = new RebuildResponse(rebuildId, 
                "Rebuild of projections for aggregate " + aggregateId + " started successfully");
            
            logger.info("Started rebuild for aggregate {} with ID: {}", aggregateId, rebuildId);
            return ResponseEntity.accepted().body(response);
            
        } catch (Exception e) {
            logger.error("Failed to start rebuild for aggregate: {}", aggregateId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new RebuildResponse(null, "Failed to start rebuild: " + e.getMessage()));
        }
    }

    /**
     * Gets the status of a specific rebuild operation.
     *
     * @param rebuildId The rebuild ID to check status for
     * @return Response containing the rebuild status
     */
    @GetMapping("/status/{rebuildId}")
    public ResponseEntity<RebuildStatusResponse> getRebuildStatus(@PathVariable("rebuildId") String rebuildId) {
        logger.debug("Received request for rebuild status: {}", rebuildId);

        ProjectionRebuildService.RebuildStatus status = projectionRebuildService.getRebuildStatus(rebuildId);
        if (status == null) {
            return ResponseEntity.notFound().build();
        }
        
        RebuildStatusResponse response = new RebuildStatusResponse(status);
        return ResponseEntity.ok(response);
    }

    /**
     * Gets the status of all rebuild operations.
     *
     * @return Response containing all rebuild statuses
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, RebuildStatusResponse>> getAllRebuildStatuses() {
        logger.debug("Received request for all rebuild statuses");
        
        Map<String, ProjectionRebuildService.RebuildStatus> statuses = projectionRebuildService.getAllRebuildStatuses();
        Map<String, RebuildStatusResponse> responseMap = statuses.entrySet().stream()
            .collect(java.util.stream.Collectors.toMap(
                Map.Entry::getKey,
                entry -> new RebuildStatusResponse(entry.getValue())
            ));
        
        return ResponseEntity.ok(responseMap);
    }

    /**
     * Cancels a running rebuild operation.
     *
     * @param rebuildId The rebuild ID to cancel
     * @return Response indicating success or failure
     */
    @PostMapping("/cancel/{rebuildId}")
    public ResponseEntity<RebuildResponse> cancelRebuild(@PathVariable("rebuildId") String rebuildId) {
        logger.info("Received request to cancel rebuild: {}", rebuildId);
        
        boolean cancelled = projectionRebuildService.cancelRebuild(rebuildId);
        if (cancelled) {
            RebuildResponse response = new RebuildResponse(rebuildId, "Rebuild cancelled successfully");
            return ResponseEntity.ok(response);
        } else {
            RebuildResponse response = new RebuildResponse(rebuildId, 
                "Rebuild not found or already completed");
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Response class for rebuild operations.
     */
    public static class RebuildResponse {
        private final String rebuildId;
        private final String message;

        public RebuildResponse(String rebuildId, String message) {
            this.rebuildId = rebuildId;
            this.message = message;
        }

        public String getRebuildId() {
            return rebuildId;
        }

        public String getMessage() {
            return message;
        }
    }

    /**
     * Response class for rebuild status information.
     */
    public static class RebuildStatusResponse {
        private final String rebuildId;
        private final String projectionType;
        private final String status;
        private final String phase;
        private final int totalEvents;
        private final int processedEvents;
        private final int errorCount;
        private final double progressPercentage;
        private final String startedAt;
        private final String completedAt;
        private final String errorMessage;

        public RebuildStatusResponse(ProjectionRebuildService.RebuildStatus status) {
            this.rebuildId = status.getRebuildId();
            this.projectionType = status.getProjectionType();
            this.status = status.getStatus().name();
            this.phase = status.getPhase();
            this.totalEvents = status.getTotalEvents();
            this.processedEvents = status.getProcessedEvents();
            this.errorCount = status.getErrorCount();
            this.progressPercentage = status.getProgressPercentage();
            this.startedAt = status.getStartedAt().toString();
            this.completedAt = status.getCompletedAt() != null ? status.getCompletedAt().toString() : null;
            this.errorMessage = status.getErrorMessage();
        }

        // Getters
        public String getRebuildId() { return rebuildId; }
        public String getProjectionType() { return projectionType; }
        public String getStatus() { return status; }
        public String getPhase() { return phase; }
        public int getTotalEvents() { return totalEvents; }
        public int getProcessedEvents() { return processedEvents; }
        public int getErrorCount() { return errorCount; }
        public double getProgressPercentage() { return progressPercentage; }
        public String getStartedAt() { return startedAt; }
        public String getCompletedAt() { return completedAt; }
        public String getErrorMessage() { return errorMessage; }
    }
}