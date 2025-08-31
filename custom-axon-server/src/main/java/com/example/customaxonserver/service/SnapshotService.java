package com.example.customaxonserver.service;

import com.example.customaxonserver.entity.EventEntity;
import com.example.customaxonserver.entity.SnapshotEntity;
import com.example.customaxonserver.repository.SnapshotRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Service for managing aggregate snapshots.
 * Provides methods for creating, storing, and retrieving snapshots to optimize
 * event replay performance and manage snapshot lifecycle.
 */
@Service
@Transactional
public class SnapshotService {

    private static final Logger logger = LoggerFactory.getLogger(SnapshotService.class);

    private final SnapshotRepository snapshotRepository;
    private final EventStoreService eventStoreService;
    private final ObjectMapper objectMapper;

    @Value("${axon.snapshot.threshold:100}")
    private int snapshotThreshold;

    @Value("${axon.snapshot.retention.days:30}")
    private int snapshotRetentionDays;

    @Value("${axon.snapshot.cleanup.enabled:true}")
    private boolean cleanupEnabled;

    @Autowired
    public SnapshotService(SnapshotRepository snapshotRepository, 
                          @Lazy EventStoreService eventStoreService,
                          ObjectMapper objectMapper) {
        this.snapshotRepository = snapshotRepository;
        this.eventStoreService = eventStoreService;
        this.objectMapper = objectMapper;
    }

    /**
     * Creates and stores a snapshot for the specified aggregate.
     *
     * @param aggregateId The unique identifier of the aggregate
     * @param aggregateType The type of the aggregate
     * @param sequenceNumber The sequence number at which the snapshot is taken
     * @param aggregateData The aggregate state data to snapshot
     * @return The created SnapshotEntity
     * @throws SnapshotException if snapshot creation fails
     */
    public SnapshotEntity createSnapshot(String aggregateId, String aggregateType, 
                                       Long sequenceNumber, Object aggregateData) {
        
        logger.debug("Creating snapshot for aggregate {} at sequence {}", aggregateId, sequenceNumber);
        
        try {
            // Convert aggregate data to JsonNode
            JsonNode snapshotData = objectMapper.valueToTree(aggregateData);
            
            // Check if snapshot already exists for this aggregate
            Optional<SnapshotEntity> existingSnapshot = snapshotRepository.findByAggregateId(aggregateId);
            
            SnapshotEntity snapshotEntity;
            if (existingSnapshot.isPresent()) {
                // Update existing snapshot
                snapshotEntity = existingSnapshot.get();
                snapshotEntity.setSequenceNumber(sequenceNumber);
                snapshotEntity.setSnapshotData(snapshotData);
                snapshotEntity.setTimestamp(OffsetDateTime.now());
                logger.debug("Updating existing snapshot for aggregate {}", aggregateId);
            } else {
                // Create new snapshot
                snapshotEntity = new SnapshotEntity(aggregateId, aggregateType, sequenceNumber, snapshotData);
                logger.debug("Creating new snapshot for aggregate {}", aggregateId);
            }
            
            SnapshotEntity savedSnapshot = snapshotRepository.save(snapshotEntity);
            
            logger.info("Successfully created snapshot for aggregate {} at sequence {}", 
                       aggregateId, sequenceNumber);
            
            return savedSnapshot;
            
        } catch (Exception e) {
            logger.error("Failed to create snapshot for aggregate {}", aggregateId, e);
            throw new SnapshotException("Failed to create snapshot for aggregate " + aggregateId, e);
        }
    }

    /**
     * Retrieves the latest snapshot for the specified aggregate.
     *
     * @param aggregateId The unique identifier of the aggregate
     * @return Optional containing the snapshot if found
     */
    @Transactional(readOnly = true)
    public Optional<SnapshotEntity> getSnapshot(String aggregateId) {
        logger.debug("Retrieving snapshot for aggregate {}", aggregateId);
        
        Optional<SnapshotEntity> snapshot = snapshotRepository.findByAggregateId(aggregateId);
        
        if (snapshot.isPresent()) {
            logger.debug("Found snapshot for aggregate {} at sequence {}", 
                        aggregateId, snapshot.get().getSequenceNumber());
        } else {
            logger.debug("No snapshot found for aggregate {}", aggregateId);
        }
        
        return snapshot;
    }

    /**
     * Checks if a snapshot should be created based on the number of events since the last snapshot.
     *
     * @param aggregateId The unique identifier of the aggregate
     * @return true if a snapshot should be created
     */
    @Transactional(readOnly = true)
    public boolean shouldCreateSnapshot(String aggregateId) {
        logger.debug("Checking if snapshot should be created for aggregate {}", aggregateId);
        
        try {
            Long currentSequenceNumber = eventStoreService.getCurrentSequenceNumber(aggregateId);
            
            if (currentSequenceNumber == 0) {
                logger.debug("No events found for aggregate {}, no snapshot needed", aggregateId);
                return false;
            }
            
            Optional<SnapshotEntity> existingSnapshot = getSnapshot(aggregateId);
            
            if (existingSnapshot.isEmpty()) {
                // No snapshot exists, check if we have enough events
                boolean shouldCreate = currentSequenceNumber >= snapshotThreshold;
                logger.debug("No existing snapshot for aggregate {}, should create: {} (events: {})", 
                           aggregateId, shouldCreate, currentSequenceNumber);
                return shouldCreate;
            }
            
            Long snapshotSequenceNumber = existingSnapshot.get().getSequenceNumber();
            Long eventsSinceSnapshot = currentSequenceNumber - snapshotSequenceNumber;
            boolean shouldCreate = eventsSinceSnapshot >= snapshotThreshold;
            
            logger.debug("Aggregate {} has {} events since last snapshot at sequence {}, should create: {}", 
                       aggregateId, eventsSinceSnapshot, snapshotSequenceNumber, shouldCreate);
            
            return shouldCreate;
            
        } catch (Exception e) {
            logger.error("Error checking if snapshot should be created for aggregate {}", aggregateId, e);
            return false;
        }
    }

    /**
     * Retrieves events for aggregate replay, starting from the latest snapshot if available.
     * This optimizes event replay by reducing the number of events that need to be processed.
     *
     * @param aggregateId The unique identifier of the aggregate
     * @return SnapshotReplayData containing snapshot and events for replay
     */
    @Transactional(readOnly = true)
    public SnapshotReplayData getEventsForReplay(String aggregateId) {
        logger.debug("Getting events for replay for aggregate {}", aggregateId);
        
        Optional<SnapshotEntity> snapshot = getSnapshot(aggregateId);
        List<EventEntity> events;
        
        if (snapshot.isPresent()) {
            // Start replay from snapshot sequence number + 1
            Long fromSequenceNumber = snapshot.get().getSequenceNumber() + 1;
            events = eventStoreService.getEventsForAggregate(aggregateId, fromSequenceNumber);
            
            logger.debug("Found snapshot for aggregate {} at sequence {}, loading {} events from sequence {}", 
                       aggregateId, snapshot.get().getSequenceNumber(), events.size(), fromSequenceNumber);
        } else {
            // No snapshot, load all events
            events = eventStoreService.getEventsForAggregate(aggregateId);
            
            logger.debug("No snapshot found for aggregate {}, loading all {} events", 
                       aggregateId, events.size());
        }
        
        return new SnapshotReplayData(snapshot.orElse(null), events);
    }

    /**
     * Deletes the snapshot for the specified aggregate.
     *
     * @param aggregateId The unique identifier of the aggregate
     * @return true if a snapshot was deleted, false if no snapshot existed
     */
    public boolean deleteSnapshot(String aggregateId) {
        logger.debug("Deleting snapshot for aggregate {}", aggregateId);
        
        try {
            boolean existed = snapshotRepository.existsByAggregateId(aggregateId);
            if (existed) {
                snapshotRepository.deleteByAggregateId(aggregateId);
                logger.info("Successfully deleted snapshot for aggregate {}", aggregateId);
            } else {
                logger.debug("No snapshot found to delete for aggregate {}", aggregateId);
            }
            return existed;
            
        } catch (Exception e) {
            logger.error("Failed to delete snapshot for aggregate {}", aggregateId, e);
            throw new SnapshotException("Failed to delete snapshot for aggregate " + aggregateId, e);
        }
    }

    /**
     * Gets statistics about snapshots for a specific aggregate type.
     *
     * @param aggregateType The type of aggregate
     * @return SnapshotStatistics containing count and other metrics
     */
    @Transactional(readOnly = true)
    public SnapshotStatistics getSnapshotStatistics(String aggregateType) {
        logger.debug("Getting snapshot statistics for aggregate type {}", aggregateType);
        
        try {
            List<SnapshotEntity> snapshots = snapshotRepository.findByAggregateType(aggregateType);
            long count = snapshots.size();
            
            OffsetDateTime oldestTimestamp = snapshots.stream()
                .map(SnapshotEntity::getTimestamp)
                .min(OffsetDateTime::compareTo)
                .orElse(null);
                
            OffsetDateTime newestTimestamp = snapshots.stream()
                .map(SnapshotEntity::getTimestamp)
                .max(OffsetDateTime::compareTo)
                .orElse(null);
            
            return new SnapshotStatistics(aggregateType, count, oldestTimestamp, newestTimestamp);
            
        } catch (Exception e) {
            logger.error("Failed to get snapshot statistics for aggregate type {}", aggregateType, e);
            throw new SnapshotException("Failed to get snapshot statistics for aggregate type " + aggregateType, e);
        }
    }

    /**
     * Scheduled cleanup of old snapshots based on retention policy.
     * Runs daily at 2 AM to clean up snapshots older than the retention period.
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void cleanupOldSnapshots() {
        if (!cleanupEnabled) {
            logger.debug("Snapshot cleanup is disabled, skipping");
            return;
        }
        
        logger.info("Starting scheduled cleanup of old snapshots");
        
        try {
            OffsetDateTime cutoffDate = OffsetDateTime.now().minusDays(snapshotRetentionDays);
            int deletedCount = snapshotRepository.deleteByTimestampBefore(cutoffDate);
            
            logger.info("Cleaned up {} old snapshots older than {}", deletedCount, cutoffDate);
            
        } catch (Exception e) {
            logger.error("Failed to cleanup old snapshots", e);
        }
    }

    /**
     * Manually triggers cleanup of old snapshots.
     *
     * @return Number of snapshots deleted
     */
    public int cleanupOldSnapshotsManually() {
        logger.info("Manually triggering cleanup of old snapshots");
        
        try {
            OffsetDateTime cutoffDate = OffsetDateTime.now().minusDays(snapshotRetentionDays);
            int deletedCount = snapshotRepository.deleteByTimestampBefore(cutoffDate);
            
            logger.info("Manually cleaned up {} old snapshots older than {}", deletedCount, cutoffDate);
            return deletedCount;
            
        } catch (Exception e) {
            logger.error("Failed to manually cleanup old snapshots", e);
            throw new SnapshotException("Failed to cleanup old snapshots", e);
        }
    }

    /**
     * Data class containing snapshot and events for aggregate replay.
     */
    public static class SnapshotReplayData {
        private final SnapshotEntity snapshot;
        private final List<EventEntity> events;

        public SnapshotReplayData(SnapshotEntity snapshot, List<EventEntity> events) {
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
     * Data class containing snapshot statistics for an aggregate type.
     */
    public static class SnapshotStatistics {
        private final String aggregateType;
        private final long count;
        private final OffsetDateTime oldestTimestamp;
        private final OffsetDateTime newestTimestamp;

        public SnapshotStatistics(String aggregateType, long count, 
                                OffsetDateTime oldestTimestamp, OffsetDateTime newestTimestamp) {
            this.aggregateType = aggregateType;
            this.count = count;
            this.oldestTimestamp = oldestTimestamp;
            this.newestTimestamp = newestTimestamp;
        }

        public String getAggregateType() {
            return aggregateType;
        }

        public long getCount() {
            return count;
        }

        public Optional<OffsetDateTime> getOldestTimestamp() {
            return Optional.ofNullable(oldestTimestamp);
        }

        public Optional<OffsetDateTime> getNewestTimestamp() {
            return Optional.ofNullable(newestTimestamp);
        }
    }

    /**
     * Exception thrown when snapshot operations fail.
     */
    public static class SnapshotException extends RuntimeException {
        public SnapshotException(String message) {
            super(message);
        }

        public SnapshotException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}