package com.example.customaxonserver.repository;

import com.example.customaxonserver.entity.SnapshotEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository interface for SnapshotEntity operations.
 * Provides methods for snapshot storage and retrieval operations.
 */
@Repository
public interface SnapshotRepository extends JpaRepository<SnapshotEntity, Long> {

    /**
     * Find snapshot by aggregate ID.
     */
    Optional<SnapshotEntity> findByAggregateId(String aggregateId);

    /**
     * Find snapshots by aggregate type.
     */
    List<SnapshotEntity> findByAggregateType(String aggregateType);

    /**
     * Find snapshots older than a specific timestamp.
     */
    List<SnapshotEntity> findByTimestampBefore(OffsetDateTime timestamp);

    /**
     * Delete snapshot by aggregate ID.
     */
    void deleteByAggregateId(String aggregateId);

    /**
     * Delete snapshots older than a specific timestamp.
     */
    @Modifying
    @Query("DELETE FROM SnapshotEntity s WHERE s.timestamp < :timestamp")
    int deleteByTimestampBefore(@Param("timestamp") OffsetDateTime timestamp);

    /**
     * Check if a snapshot exists for the given aggregate ID.
     */
    boolean existsByAggregateId(String aggregateId);

    /**
     * Count snapshots by aggregate type.
     */
    long countByAggregateType(String aggregateType);

    /**
     * Find the most recent snapshots (for cleanup operations).
     */
    List<SnapshotEntity> findTop10ByOrderByTimestampDesc();
}