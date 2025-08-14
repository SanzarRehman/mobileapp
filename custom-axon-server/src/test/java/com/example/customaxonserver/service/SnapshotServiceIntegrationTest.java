package com.example.customaxonserver.service;

import com.example.customaxonserver.entity.EventEntity;
import com.example.customaxonserver.entity.SnapshotEntity;
import com.example.customaxonserver.repository.EventRepository;
import com.example.customaxonserver.repository.SnapshotRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for SnapshotService.
 * Tests the complete snapshot lifecycle with real database interactions.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "axon.snapshot.threshold=5",
    "axon.snapshot.retention.days=1",
    "axon.snapshot.cleanup.enabled=true"
})
@Transactional
class SnapshotServiceIntegrationTest {

    @Autowired
    private SnapshotService snapshotService;

    @Autowired
    private EventStoreService eventStoreService;

    @Autowired
    private SnapshotRepository snapshotRepository;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private static final String AGGREGATE_ID = "integration-test-user-123";
    private static final String AGGREGATE_TYPE = "UserAggregate";

    @BeforeEach
    void setUp() {
        // Clean up any existing test data - use try-catch to handle table not existing
        try {
            snapshotRepository.deleteByAggregateId(AGGREGATE_ID);
        } catch (Exception e) {
            // Ignore - table might not exist yet
        }
        try {
            eventRepository.deleteByAggregateId(AGGREGATE_ID);
        } catch (Exception e) {
            // Ignore - table might not exist yet
        }
    }

    @Test
    void completeSnapshotLifecycle_ShouldWorkEndToEnd() {
        // Given - Create some events first
        createTestEvents(10);

        // When - Create a snapshot
        TestAggregateData aggregateData = new TestAggregateData("John Doe", "john@example.com", 25);
        SnapshotEntity createdSnapshot = snapshotService.createSnapshot(
            AGGREGATE_ID, AGGREGATE_TYPE, 10L, aggregateData);

        // Then - Verify snapshot was created
        assertThat(createdSnapshot).isNotNull();
        assertThat(createdSnapshot.getId()).isNotNull();
        assertThat(createdSnapshot.getAggregateId()).isEqualTo(AGGREGATE_ID);
        assertThat(createdSnapshot.getAggregateType()).isEqualTo(AGGREGATE_TYPE);
        assertThat(createdSnapshot.getSequenceNumber()).isEqualTo(10L);

        // Verify we can retrieve the snapshot
        Optional<SnapshotEntity> retrievedSnapshot = snapshotService.getSnapshot(AGGREGATE_ID);
        assertThat(retrievedSnapshot).isPresent();
        assertThat(retrievedSnapshot.get().getId()).isEqualTo(createdSnapshot.getId());

        // Verify snapshot data can be deserialized
        JsonNode snapshotData = retrievedSnapshot.get().getSnapshotData();
        assertThat(snapshotData.get("name").asText()).isEqualTo("John Doe");
        assertThat(snapshotData.get("email").asText()).isEqualTo("john@example.com");
        assertThat(snapshotData.get("age").asInt()).isEqualTo(25);
    }

    @Test
    void shouldCreateSnapshot_WithRealEvents_ShouldWorkCorrectly() {
        // Given - Create events below threshold
        createTestEvents(3);

        // When - Check if snapshot should be created
        boolean shouldCreateBelowThreshold = snapshotService.shouldCreateSnapshot(AGGREGATE_ID);

        // Then - Should not create snapshot below threshold
        assertThat(shouldCreateBelowThreshold).isFalse();

        // Given - Create more events to exceed threshold
        createTestEvents(5, 4L); // Start from sequence 4

        // When - Check if snapshot should be created
        boolean shouldCreateAboveThreshold = snapshotService.shouldCreateSnapshot(AGGREGATE_ID);

        // Then - Should create snapshot above threshold
        assertThat(shouldCreateAboveThreshold).isTrue();
    }

    @Test
    void getEventsForReplay_WithRealData_ShouldOptimizeReplay() {
        // Given - Create events and a snapshot
        createTestEvents(15);
        
        TestAggregateData aggregateData = new TestAggregateData("Snapshot User", "snapshot@example.com", 30);
        snapshotService.createSnapshot(AGGREGATE_ID, AGGREGATE_TYPE, 10L, aggregateData);
        
        // Create more events after snapshot
        createTestEvents(5, 11L);

        // When - Get events for replay
        SnapshotService.SnapshotReplayData replayData = snapshotService.getEventsForReplay(AGGREGATE_ID);

        // Then - Should return snapshot and only events after snapshot
        assertThat(replayData.hasSnapshot()).isTrue();
        assertThat(replayData.getSnapshot()).isPresent();
        assertThat(replayData.getSnapshot().get().getSequenceNumber()).isEqualTo(10L);
        
        // Should only return events 11-15 (5 events)
        assertThat(replayData.getEvents()).hasSize(5);
        assertThat(replayData.getEvents().get(0).getSequenceNumber()).isEqualTo(11L);
        assertThat(replayData.getEvents().get(4).getSequenceNumber()).isEqualTo(15L);
    }

    @Test
    void getEventsForReplay_NoSnapshot_ShouldReturnAllEvents() {
        // Given - Create events without snapshot
        createTestEvents(8);

        // When - Get events for replay
        SnapshotService.SnapshotReplayData replayData = snapshotService.getEventsForReplay(AGGREGATE_ID);

        // Then - Should return all events without snapshot
        assertThat(replayData.hasSnapshot()).isFalse();
        assertThat(replayData.getSnapshot()).isEmpty();
        assertThat(replayData.getEvents()).hasSize(8);
        assertThat(replayData.getStartingSequenceNumber()).isEqualTo(0L);
    }

    @Test
    void updateExistingSnapshot_ShouldReplaceOldSnapshot() {
        // Given - Create initial snapshot
        TestAggregateData initialData = new TestAggregateData("Initial User", "initial@example.com", 20);
        SnapshotEntity initialSnapshot = snapshotService.createSnapshot(
            AGGREGATE_ID, AGGREGATE_TYPE, 5L, initialData);

        // When - Update with new snapshot data
        TestAggregateData updatedData = new TestAggregateData("Updated User", "updated@example.com", 25);
        SnapshotEntity updatedSnapshot = snapshotService.createSnapshot(
            AGGREGATE_ID, AGGREGATE_TYPE, 10L, updatedData);

        // Then - Should have same ID but updated data
        assertThat(updatedSnapshot.getId()).isEqualTo(initialSnapshot.getId());
        assertThat(updatedSnapshot.getSequenceNumber()).isEqualTo(10L);
        
        // Verify only one snapshot exists
        List<SnapshotEntity> allSnapshots = snapshotRepository.findByAggregateId(AGGREGATE_ID).stream().toList();
        assertThat(allSnapshots).hasSize(1);
        
        // Verify updated data
        JsonNode snapshotData = updatedSnapshot.getSnapshotData();
        assertThat(snapshotData.get("name").asText()).isEqualTo("Updated User");
        assertThat(snapshotData.get("email").asText()).isEqualTo("updated@example.com");
        assertThat(snapshotData.get("age").asInt()).isEqualTo(25);
    }

    @Test
    void deleteSnapshot_ShouldRemoveFromDatabase() {
        // Given - Create a snapshot
        TestAggregateData aggregateData = new TestAggregateData("To Delete", "delete@example.com", 30);
        snapshotService.createSnapshot(AGGREGATE_ID, AGGREGATE_TYPE, 5L, aggregateData);
        
        // Verify snapshot exists
        assertThat(snapshotService.getSnapshot(AGGREGATE_ID)).isPresent();

        // When - Delete the snapshot
        boolean deleted = snapshotService.deleteSnapshot(AGGREGATE_ID);

        // Then - Should be deleted
        assertThat(deleted).isTrue();
        assertThat(snapshotService.getSnapshot(AGGREGATE_ID)).isEmpty();
        
        // Verify from repository directly
        assertThat(snapshotRepository.findByAggregateId(AGGREGATE_ID)).isEmpty();
    }

    @Test
    void getSnapshotStatistics_WithRealData_ShouldReturnAccurateStats() {
        // Given - Create snapshots for different aggregates of same type
        String aggregateId1 = AGGREGATE_ID + "-1";
        String aggregateId2 = AGGREGATE_ID + "-2";
        
        TestAggregateData data1 = new TestAggregateData("User 1", "user1@example.com", 25);
        TestAggregateData data2 = new TestAggregateData("User 2", "user2@example.com", 30);
        
        SnapshotEntity snapshot1 = snapshotService.createSnapshot(aggregateId1, AGGREGATE_TYPE, 5L, data1);
        
        // Create second snapshot with slight delay to test timestamp ordering
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        SnapshotEntity snapshot2 = snapshotService.createSnapshot(aggregateId2, AGGREGATE_TYPE, 10L, data2);

        // When - Get statistics
        SnapshotService.SnapshotStatistics stats = snapshotService.getSnapshotStatistics(AGGREGATE_TYPE);

        // Then - Should return accurate statistics
        assertThat(stats.getAggregateType()).isEqualTo(AGGREGATE_TYPE);
        assertThat(stats.getCount()).isEqualTo(2);
        assertThat(stats.getOldestTimestamp()).isPresent();
        assertThat(stats.getNewestTimestamp()).isPresent();
        
        // Verify timestamp ordering
        assertThat(stats.getOldestTimestamp().get()).isBeforeOrEqualTo(stats.getNewestTimestamp().get());

        // Cleanup
        snapshotService.deleteSnapshot(aggregateId1);
        snapshotService.deleteSnapshot(aggregateId2);
    }

    @Test
    void cleanupOldSnapshots_ShouldRemoveExpiredSnapshots() {
        // Given - Create an old snapshot (simulate by setting timestamp in the past)
        TestAggregateData aggregateData = new TestAggregateData("Old User", "old@example.com", 40);
        SnapshotEntity snapshot = snapshotService.createSnapshot(AGGREGATE_ID, AGGREGATE_TYPE, 5L, aggregateData);
        
        // Manually set timestamp to be older than retention period
        OffsetDateTime oldTimestamp = OffsetDateTime.now().minusDays(2);
        snapshot.setTimestamp(oldTimestamp);
        snapshotRepository.save(snapshot);

        // When - Trigger cleanup
        int deletedCount = snapshotService.cleanupOldSnapshotsManually();

        // Then - Should delete the old snapshot
        assertThat(deletedCount).isEqualTo(1);
        assertThat(snapshotService.getSnapshot(AGGREGATE_ID)).isEmpty();
    }

    // Helper methods

    private void createTestEvents(int count) {
        createTestEvents(count, 1L);
    }

    private void createTestEvents(int count, Long startingSequence) {
        for (int i = 0; i < count; i++) {
            Long sequenceNumber = startingSequence + i;
            TestEventData eventData = new TestEventData("Event " + sequenceNumber, "test-value-" + sequenceNumber);
            
            try {
                eventStoreService.storeEvent(
                    AGGREGATE_ID,
                    AGGREGATE_TYPE,
                    sequenceNumber,
                    "TestEvent",
                    eventData,
                    null
                );
            } catch (EventStoreService.ConcurrencyException e) {
                // Skip if event already exists (for test setup)
            }
        }
    }

    /**
     * Test aggregate data class for integration testing.
     */
    private static class TestAggregateData {
        private final String name;
        private final String email;
        private final int age;

        public TestAggregateData(String name, String email, int age) {
            this.name = name;
            this.email = email;
            this.age = age;
        }

        public String getName() {
            return name;
        }

        public String getEmail() {
            return email;
        }

        public int getAge() {
            return age;
        }
    }

    /**
     * Test event data class for integration testing.
     */
    private static class TestEventData {
        private final String description;
        private final String value;

        public TestEventData(String description, String value) {
            this.description = description;
            this.value = value;
        }

        public String getDescription() {
            return description;
        }

        public String getValue() {
            return value;
        }
    }
}