package com.example.customaxonserver.repository;

import com.example.customaxonserver.entity.EventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository interface for EventEntity operations.
 * Provides methods for event store operations including querying and persistence.
 */
@Repository
public interface EventRepository extends JpaRepository<EventEntity, Long> {

    /**
     * Find all events for a specific aggregate ordered by sequence number.
     */
    List<EventEntity> findByAggregateIdOrderBySequenceNumber(String aggregateId);

    /**
     * Find events for a specific aggregate starting from a given sequence number.
     */
    List<EventEntity> findByAggregateIdAndSequenceNumberGreaterThanEqualOrderBySequenceNumber(
            String aggregateId, Long fromSequenceNumber);

    /**
     * Find the latest event for a specific aggregate.
     */
    Optional<EventEntity> findTopByAggregateIdOrderBySequenceNumberDesc(String aggregateId);

    /**
     * Find events by aggregate type within a time range.
     */
    List<EventEntity> findByAggregateTypeAndTimestampBetweenOrderByTimestamp(
            String aggregateType, OffsetDateTime from, OffsetDateTime to);

    /**
     * Find events by event type within a time range.
     */
    List<EventEntity> findByEventTypeAndTimestampBetweenOrderByTimestamp(
            String eventType, OffsetDateTime from, OffsetDateTime to);

    /**
     * Get the next sequence number for an aggregate.
     */
    @Query("SELECT COALESCE(MAX(e.sequenceNumber), 0) + 1 FROM EventEntity e WHERE e.aggregateId = :aggregateId")
    Long getNextSequenceNumber(@Param("aggregateId") String aggregateId);

    /**
     * Check if an event with the given aggregate ID and sequence number exists.
     */
    boolean existsByAggregateIdAndSequenceNumber(String aggregateId, Long sequenceNumber);

    /**
     * Count events for a specific aggregate.
     */
    long countByAggregateId(String aggregateId);

    /**
     * Find events after a specific timestamp for event replay.
     */
    List<EventEntity> findByTimestampGreaterThanOrderByTimestamp(OffsetDateTime timestamp);

    /**
     * Delete all events for a specific aggregate (used for testing).
     */
    void deleteByAggregateId(String aggregateId);
}