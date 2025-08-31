package com.example.customaxonserver.entity;

import com.fasterxml.jackson.databind.JsonNode;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

/**
 * JPA Entity representing an aggregate snapshot in the event store.
 * Maps to the 'snapshots' table in PostgreSQL.
 */
@Entity
@Table(name = "snapshots",
       uniqueConstraints = @UniqueConstraint(
           name = "uk_snapshots_aggregate", 
           columnNames = {"aggregate_id"}
       ))
public class SnapshotEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "aggregate_id", nullable = false, unique = true)
    private String aggregateId;

    @Column(name = "aggregate_type", nullable = false)
    private String aggregateType;

    @Column(name = "sequence_number", nullable = false)
    private Long sequenceNumber;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "snapshot_data", nullable = false, columnDefinition = "jsonb")
    private JsonNode snapshotData;

    @Column(name = "timestamp", nullable = false)
    private OffsetDateTime timestamp;

    @Version
    @Column(name = "version")
    private Long version;

    // Default constructor
    public SnapshotEntity() {
        this.timestamp = OffsetDateTime.now();
    }

    // Constructor with required fields
    public SnapshotEntity(String aggregateId, String aggregateType, Long sequenceNumber, 
                         JsonNode snapshotData) {
        this();
        this.aggregateId = aggregateId;
        this.aggregateType = aggregateType;
        this.sequenceNumber = sequenceNumber;
        this.snapshotData = snapshotData;
    }

    // Getters and setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getAggregateId() {
        return aggregateId;
    }

    public void setAggregateId(String aggregateId) {
        this.aggregateId = aggregateId;
    }

    public String getAggregateType() {
        return aggregateType;
    }

    public void setAggregateType(String aggregateType) {
        this.aggregateType = aggregateType;
    }

    public Long getSequenceNumber() {
        return sequenceNumber;
    }

    public void setSequenceNumber(Long sequenceNumber) {
        this.sequenceNumber = sequenceNumber;
    }

    public JsonNode getSnapshotData() {
        return snapshotData;
    }

    public void setSnapshotData(JsonNode snapshotData) {
        this.snapshotData = snapshotData;
    }

    public OffsetDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(OffsetDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    @Override
    public String toString() {
        return "SnapshotEntity{" +
                "id=" + id +
                ", aggregateId='" + aggregateId + '\'' +
                ", aggregateType='" + aggregateType + '\'' +
                ", sequenceNumber=" + sequenceNumber +
                ", timestamp=" + timestamp +
                ", version=" + version +
                '}';
    }
}