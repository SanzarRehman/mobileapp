package com.example.customaxonserver.service;

import com.example.customaxonserver.entity.EventEntity;
import com.example.customaxonserver.entity.SnapshotEntity;
import com.example.customaxonserver.repository.SnapshotRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for SnapshotService.
 * Tests snapshot creation, retrieval, lifecycle management, and integration with event replay.
 */
@ExtendWith(MockitoExtension.class)
class SnapshotServiceTest {

    @Mock
    private SnapshotRepository snapshotRepository;

    @Mock
    private EventStoreService eventStoreService;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private SnapshotService snapshotService;

    private static final String AGGREGATE_ID = "user-123";
    private static final String AGGREGATE_TYPE = "UserAggregate";
    private static final Long SEQUENCE_NUMBER = 150L;

    @BeforeEach
    void setUp() {
        // Set configuration properties
        ReflectionTestUtils.setField(snapshotService, "snapshotThreshold", 100);
        ReflectionTestUtils.setField(snapshotService, "snapshotRetentionDays", 30);
        ReflectionTestUtils.setField(snapshotService, "cleanupEnabled", true);
    }

    @Test
    void createSnapshot_NewSnapshot_ShouldCreateSuccessfully() {
        // Given
        TestAggregateData aggregateData = new TestAggregateData("John Doe", "john@example.com");
        JsonNode snapshotDataNode = mock(JsonNode.class);
        SnapshotEntity savedSnapshot = createTestSnapshot();

        when(objectMapper.valueToTree(aggregateData)).thenReturn(snapshotDataNode);
        when(snapshotRepository.findByAggregateId(AGGREGATE_ID)).thenReturn(Optional.empty());
        when(snapshotRepository.save(any(SnapshotEntity.class))).thenReturn(savedSnapshot);

        // When
        SnapshotEntity result = snapshotService.createSnapshot(AGGREGATE_ID, AGGREGATE_TYPE, 
                                                              SEQUENCE_NUMBER, aggregateData);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getAggregateId()).isEqualTo(AGGREGATE_ID);
        assertThat(result.getAggregateType()).isEqualTo(AGGREGATE_TYPE);
        assertThat(result.getSequenceNumber()).isEqualTo(SEQUENCE_NUMBER);

        verify(objectMapper).valueToTree(aggregateData);
        verify(snapshotRepository).findByAggregateId(AGGREGATE_ID);
        verify(snapshotRepository).save(any(SnapshotEntity.class));
    }

    @Test
    void createSnapshot_ExistingSnapshot_ShouldUpdateSuccessfully() {
        // Given
        TestAggregateData aggregateData = new TestAggregateData("John Doe Updated", "john.updated@example.com");
        JsonNode snapshotDataNode = mock(JsonNode.class);
        SnapshotEntity existingSnapshot = mock(SnapshotEntity.class);
        SnapshotEntity updatedSnapshot = createTestSnapshot();
        updatedSnapshot.setSequenceNumber(200L);

        when(objectMapper.valueToTree(aggregateData)).thenReturn(snapshotDataNode);
        when(snapshotRepository.findByAggregateId(AGGREGATE_ID)).thenReturn(Optional.of(existingSnapshot));
        when(snapshotRepository.save(existingSnapshot)).thenReturn(updatedSnapshot);

        // When
        SnapshotEntity result = snapshotService.createSnapshot(AGGREGATE_ID, AGGREGATE_TYPE, 
                                                              200L, aggregateData);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getSequenceNumber()).isEqualTo(200L);

        verify(existingSnapshot).setSequenceNumber(200L);
        verify(existingSnapshot).setSnapshotData(snapshotDataNode);
        verify(existingSnapshot).setTimestamp(any(OffsetDateTime.class));
        verify(snapshotRepository).save(existingSnapshot);
    }

    @Test
    void createSnapshot_SerializationError_ShouldThrowSnapshotException() {
        // Given
        TestAggregateData aggregateData = new TestAggregateData("John Doe", "john@example.com");
        
        when(objectMapper.valueToTree(aggregateData)).thenThrow(new RuntimeException("Serialization failed"));

        // When & Then
        assertThatThrownBy(() -> snapshotService.createSnapshot(AGGREGATE_ID, AGGREGATE_TYPE, 
                                                               SEQUENCE_NUMBER, aggregateData))
            .isInstanceOf(SnapshotService.SnapshotException.class)
            .hasMessageContaining("Failed to create snapshot for aggregate " + AGGREGATE_ID);

        verify(snapshotRepository, never()).save(any());
    }

    @Test
    void getSnapshot_ExistingSnapshot_ShouldReturnSnapshot() {
        // Given
        SnapshotEntity existingSnapshot = createTestSnapshot();
        when(snapshotRepository.findByAggregateId(AGGREGATE_ID)).thenReturn(Optional.of(existingSnapshot));

        // When
        Optional<SnapshotEntity> result = snapshotService.getSnapshot(AGGREGATE_ID);

        // Then
        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo(existingSnapshot);

        verify(snapshotRepository).findByAggregateId(AGGREGATE_ID);
    }

    @Test
    void getSnapshot_NoSnapshot_ShouldReturnEmpty() {
        // Given
        when(snapshotRepository.findByAggregateId(AGGREGATE_ID)).thenReturn(Optional.empty());

        // When
        Optional<SnapshotEntity> result = snapshotService.getSnapshot(AGGREGATE_ID);

        // Then
        assertThat(result).isEmpty();

        verify(snapshotRepository).findByAggregateId(AGGREGATE_ID);
    }

    @Test
    void shouldCreateSnapshot_NoExistingSnapshot_AboveThreshold_ShouldReturnTrue() {
        // Given
        when(eventStoreService.getCurrentSequenceNumber(AGGREGATE_ID)).thenReturn(150L);
        when(snapshotRepository.findByAggregateId(AGGREGATE_ID)).thenReturn(Optional.empty());

        // When
        boolean result = snapshotService.shouldCreateSnapshot(AGGREGATE_ID);

        // Then
        assertThat(result).isTrue();

        verify(eventStoreService).getCurrentSequenceNumber(AGGREGATE_ID);
        verify(snapshotRepository).findByAggregateId(AGGREGATE_ID);
    }

    @Test
    void shouldCreateSnapshot_NoExistingSnapshot_BelowThreshold_ShouldReturnFalse() {
        // Given
        when(eventStoreService.getCurrentSequenceNumber(AGGREGATE_ID)).thenReturn(50L);
        when(snapshotRepository.findByAggregateId(AGGREGATE_ID)).thenReturn(Optional.empty());

        // When
        boolean result = snapshotService.shouldCreateSnapshot(AGGREGATE_ID);

        // Then
        assertThat(result).isFalse();

        verify(eventStoreService).getCurrentSequenceNumber(AGGREGATE_ID);
        verify(snapshotRepository).findByAggregateId(AGGREGATE_ID);
    }

    @Test
    void shouldCreateSnapshot_ExistingSnapshot_AboveThreshold_ShouldReturnTrue() {
        // Given
        SnapshotEntity existingSnapshot = createTestSnapshot();
        existingSnapshot.setSequenceNumber(100L);
        
        when(eventStoreService.getCurrentSequenceNumber(AGGREGATE_ID)).thenReturn(250L);
        when(snapshotRepository.findByAggregateId(AGGREGATE_ID)).thenReturn(Optional.of(existingSnapshot));

        // When
        boolean result = snapshotService.shouldCreateSnapshot(AGGREGATE_ID);

        // Then
        assertThat(result).isTrue();

        verify(eventStoreService).getCurrentSequenceNumber(AGGREGATE_ID);
        verify(snapshotRepository).findByAggregateId(AGGREGATE_ID);
    }

    @Test
    void shouldCreateSnapshot_ExistingSnapshot_BelowThreshold_ShouldReturnFalse() {
        // Given
        SnapshotEntity existingSnapshot = createTestSnapshot();
        existingSnapshot.setSequenceNumber(100L);
        
        when(eventStoreService.getCurrentSequenceNumber(AGGREGATE_ID)).thenReturn(150L);
        when(snapshotRepository.findByAggregateId(AGGREGATE_ID)).thenReturn(Optional.of(existingSnapshot));

        // When
        boolean result = snapshotService.shouldCreateSnapshot(AGGREGATE_ID);

        // Then
        assertThat(result).isFalse();

        verify(eventStoreService).getCurrentSequenceNumber(AGGREGATE_ID);
        verify(snapshotRepository).findByAggregateId(AGGREGATE_ID);
    }

    @Test
    void shouldCreateSnapshot_NoEvents_ShouldReturnFalse() {
        // Given
        when(eventStoreService.getCurrentSequenceNumber(AGGREGATE_ID)).thenReturn(0L);

        // When
        boolean result = snapshotService.shouldCreateSnapshot(AGGREGATE_ID);

        // Then
        assertThat(result).isFalse();

        verify(eventStoreService).getCurrentSequenceNumber(AGGREGATE_ID);
        verify(snapshotRepository, never()).findByAggregateId(any());
    }

    @Test
    void getEventsForReplay_WithSnapshot_ShouldReturnSnapshotAndSubsequentEvents() {
        // Given
        SnapshotEntity existingSnapshot = createTestSnapshot();
        existingSnapshot.setSequenceNumber(100L);
        
        List<EventEntity> events = Arrays.asList(
            createTestEvent(101L),
            createTestEvent(102L),
            createTestEvent(103L)
        );

        when(snapshotRepository.findByAggregateId(AGGREGATE_ID)).thenReturn(Optional.of(existingSnapshot));
        when(eventStoreService.getEventsForAggregate(AGGREGATE_ID, 101L)).thenReturn(events);

        // When
        SnapshotService.SnapshotReplayData result = snapshotService.getEventsForReplay(AGGREGATE_ID);

        // Then
        assertThat(result.hasSnapshot()).isTrue();
        assertThat(result.getSnapshot()).isPresent();
        assertThat(result.getSnapshot().get()).isEqualTo(existingSnapshot);
        assertThat(result.getEvents()).hasSize(3);
        assertThat(result.getStartingSequenceNumber()).isEqualTo(100L);

        verify(snapshotRepository).findByAggregateId(AGGREGATE_ID);
        verify(eventStoreService).getEventsForAggregate(AGGREGATE_ID, 101L);
    }

    @Test
    void getEventsForReplay_NoSnapshot_ShouldReturnAllEvents() {
        // Given
        List<EventEntity> events = Arrays.asList(
            createTestEvent(1L),
            createTestEvent(2L),
            createTestEvent(3L)
        );

        when(snapshotRepository.findByAggregateId(AGGREGATE_ID)).thenReturn(Optional.empty());
        when(eventStoreService.getEventsForAggregate(AGGREGATE_ID)).thenReturn(events);

        // When
        SnapshotService.SnapshotReplayData result = snapshotService.getEventsForReplay(AGGREGATE_ID);

        // Then
        assertThat(result.hasSnapshot()).isFalse();
        assertThat(result.getSnapshot()).isEmpty();
        assertThat(result.getEvents()).hasSize(3);
        assertThat(result.getStartingSequenceNumber()).isEqualTo(0L);

        verify(snapshotRepository).findByAggregateId(AGGREGATE_ID);
        verify(eventStoreService).getEventsForAggregate(AGGREGATE_ID);
    }

    @Test
    void deleteSnapshot_ExistingSnapshot_ShouldReturnTrue() {
        // Given
        when(snapshotRepository.existsByAggregateId(AGGREGATE_ID)).thenReturn(true);

        // When
        boolean result = snapshotService.deleteSnapshot(AGGREGATE_ID);

        // Then
        assertThat(result).isTrue();

        verify(snapshotRepository).existsByAggregateId(AGGREGATE_ID);
        verify(snapshotRepository).deleteByAggregateId(AGGREGATE_ID);
    }

    @Test
    void deleteSnapshot_NoSnapshot_ShouldReturnFalse() {
        // Given
        when(snapshotRepository.existsByAggregateId(AGGREGATE_ID)).thenReturn(false);

        // When
        boolean result = snapshotService.deleteSnapshot(AGGREGATE_ID);

        // Then
        assertThat(result).isFalse();

        verify(snapshotRepository).existsByAggregateId(AGGREGATE_ID);
        verify(snapshotRepository, never()).deleteByAggregateId(any());
    }

    @Test
    void deleteSnapshot_RepositoryError_ShouldThrowSnapshotException() {
        // Given
        when(snapshotRepository.existsByAggregateId(AGGREGATE_ID)).thenReturn(true);
        doThrow(new RuntimeException("Database error")).when(snapshotRepository).deleteByAggregateId(AGGREGATE_ID);

        // When & Then
        assertThatThrownBy(() -> snapshotService.deleteSnapshot(AGGREGATE_ID))
            .isInstanceOf(SnapshotService.SnapshotException.class)
            .hasMessageContaining("Failed to delete snapshot for aggregate " + AGGREGATE_ID);

        verify(snapshotRepository).existsByAggregateId(AGGREGATE_ID);
        verify(snapshotRepository).deleteByAggregateId(AGGREGATE_ID);
    }

    @Test
    void getSnapshotStatistics_WithSnapshots_ShouldReturnStatistics() {
        // Given
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime yesterday = now.minusDays(1);
        
        List<SnapshotEntity> snapshots = Arrays.asList(
            createTestSnapshotWithTimestamp(yesterday),
            createTestSnapshotWithTimestamp(now)
        );

        when(snapshotRepository.findByAggregateType(AGGREGATE_TYPE)).thenReturn(snapshots);

        // When
        SnapshotService.SnapshotStatistics result = snapshotService.getSnapshotStatistics(AGGREGATE_TYPE);

        // Then
        assertThat(result.getAggregateType()).isEqualTo(AGGREGATE_TYPE);
        assertThat(result.getCount()).isEqualTo(2);
        assertThat(result.getOldestTimestamp()).isPresent();
        assertThat(result.getOldestTimestamp().get()).isEqualTo(yesterday);
        assertThat(result.getNewestTimestamp()).isPresent();
        assertThat(result.getNewestTimestamp().get()).isEqualTo(now);

        verify(snapshotRepository).findByAggregateType(AGGREGATE_TYPE);
    }

    @Test
    void getSnapshotStatistics_NoSnapshots_ShouldReturnEmptyStatistics() {
        // Given
        when(snapshotRepository.findByAggregateType(AGGREGATE_TYPE)).thenReturn(Arrays.asList());

        // When
        SnapshotService.SnapshotStatistics result = snapshotService.getSnapshotStatistics(AGGREGATE_TYPE);

        // Then
        assertThat(result.getAggregateType()).isEqualTo(AGGREGATE_TYPE);
        assertThat(result.getCount()).isEqualTo(0);
        assertThat(result.getOldestTimestamp()).isEmpty();
        assertThat(result.getNewestTimestamp()).isEmpty();

        verify(snapshotRepository).findByAggregateType(AGGREGATE_TYPE);
    }

    @Test
    void cleanupOldSnapshotsManually_ShouldDeleteOldSnapshots() {
        // Given
        when(snapshotRepository.deleteByTimestampBefore(any(OffsetDateTime.class))).thenReturn(5);

        // When
        int result = snapshotService.cleanupOldSnapshotsManually();

        // Then
        assertThat(result).isEqualTo(5);

        verify(snapshotRepository).deleteByTimestampBefore(any(OffsetDateTime.class));
    }

    @Test
    void cleanupOldSnapshotsManually_RepositoryError_ShouldThrowSnapshotException() {
        // Given
        when(snapshotRepository.deleteByTimestampBefore(any(OffsetDateTime.class)))
            .thenThrow(new RuntimeException("Database error"));

        // When & Then
        assertThatThrownBy(() -> snapshotService.cleanupOldSnapshotsManually())
            .isInstanceOf(SnapshotService.SnapshotException.class)
            .hasMessageContaining("Failed to cleanup old snapshots");

        verify(snapshotRepository).deleteByTimestampBefore(any(OffsetDateTime.class));
    }

    // Helper methods for creating test data

    private SnapshotEntity createTestSnapshot() {
        SnapshotEntity snapshot = new SnapshotEntity();
        snapshot.setId(1L);
        snapshot.setAggregateId(AGGREGATE_ID);
        snapshot.setAggregateType(AGGREGATE_TYPE);
        snapshot.setSequenceNumber(SEQUENCE_NUMBER);
        snapshot.setTimestamp(OffsetDateTime.now());
        return snapshot;
    }

    private SnapshotEntity createTestSnapshotWithTimestamp(OffsetDateTime timestamp) {
        SnapshotEntity snapshot = createTestSnapshot();
        snapshot.setTimestamp(timestamp);
        return snapshot;
    }

    private EventEntity createTestEvent(Long sequenceNumber) {
        EventEntity event = new EventEntity();
        event.setId(sequenceNumber);
        event.setAggregateId(AGGREGATE_ID);
        event.setAggregateType(AGGREGATE_TYPE);
        event.setSequenceNumber(sequenceNumber);
        event.setEventType("TestEvent");
        event.setTimestamp(OffsetDateTime.now());
        return event;
    }

    /**
     * Test aggregate data class for testing serialization.
     */
    private static class TestAggregateData {
        private final String name;
        private final String email;

        public TestAggregateData(String name, String email) {
            this.name = name;
            this.email = email;
        }

        public String getName() {
            return name;
        }

        public String getEmail() {
            return email;
        }
    }
}