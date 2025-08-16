package com.example.customaxonserver.entity;

import com.fasterxml.jackson.databind.JsonNode;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

/**
 * JPA Entity representing an event in the event store.
 * Maps to the 'events' table in PostgreSQL.
 */
@Entity
@Table(name = "events", 
       uniqueConstraints = @UniqueConstraint(
           name = "uk_events_aggregate_sequence", 
           columnNames = {"aggregate_id", "sequence_number"}
       ))
public class EventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "aggregate_id", nullable = false)
    private String aggregateId;

    @Column(name = "aggregate_type", nullable = false)
    private String aggregateType;

    @Column(name = "sequence_number", nullable = false)
    private Long sequenceNumber;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "event_data", nullable = false, columnDefinition = "jsonb")
    private JsonNode eventData;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private JsonNode metadata;

    @Column(name = "timestamp", nullable = false)
    private OffsetDateTime timestamp;

    @Version
    @Column(name = "version")
    private Long version;

    // Default constructor
    public EventEntity() {
        this.timestamp = OffsetDateTime.now();
    }

    // Constructor with required fields
    public EventEntity(String aggregateId, String aggregateType, Long sequenceNumber, 
                      String eventType, JsonNode eventData) {
        this();
        this.aggregateId = aggregateId;
        this.aggregateType = aggregateType;
        this.sequenceNumber = sequenceNumber;
        this.eventType = eventType;
        this.eventData = eventData;
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

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public JsonNode getEventData() {
        return eventData;
    }

    public void setEventData(JsonNode eventData) {
        this.eventData = eventData;
    }

    public JsonNode getMetadata() {
        return metadata;
    }

    public void setMetadata(JsonNode metadata) {
        this.metadata = metadata;
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
        return "EventEntity{" +
                "id=" + id +
                ", aggregateId='" + aggregateId + '\'' +
                ", aggregateType='" + aggregateType + '\'' +
                ", sequenceNumber=" + sequenceNumber +
                ", eventType='" + eventType + '\'' +
                ", timestamp=" + timestamp +
                ", version=" + version +
                '}';
    }
}