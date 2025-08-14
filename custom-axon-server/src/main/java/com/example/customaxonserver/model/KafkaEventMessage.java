package com.example.customaxonserver.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Represents an event message that will be published to Kafka.
 * Contains all necessary information for event distribution and processing.
 */
public class KafkaEventMessage {

    private final String eventId;
    private final String aggregateId;
    private final String aggregateType;
    private final Long sequenceNumber;
    private final String eventType;
    private final Object eventData;
    private final Map<String, Object> metadata;
    private final Instant timestamp;

    @JsonCreator
    public KafkaEventMessage(
            @JsonProperty("eventId") String eventId,
            @JsonProperty("aggregateId") String aggregateId,
            @JsonProperty("aggregateType") String aggregateType,
            @JsonProperty("sequenceNumber") Long sequenceNumber,
            @JsonProperty("eventType") String eventType,
            @JsonProperty("eventData") Object eventData,
            @JsonProperty("metadata") Map<String, Object> metadata,
            @JsonProperty("timestamp") Instant timestamp) {
        this.eventId = eventId;
        this.aggregateId = aggregateId;
        this.aggregateType = aggregateType;
        this.sequenceNumber = sequenceNumber;
        this.eventType = eventType;
        this.eventData = eventData;
        this.metadata = metadata;
        this.timestamp = timestamp;
    }

    public String getEventId() {
        return eventId;
    }

    public String getAggregateId() {
        return aggregateId;
    }

    public String getAggregateType() {
        return aggregateType;
    }

    public Long getSequenceNumber() {
        return sequenceNumber;
    }

    public String getEventType() {
        return eventType;
    }

    public Object getEventData() {
        return eventData;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        KafkaEventMessage that = (KafkaEventMessage) o;
        return Objects.equals(eventId, that.eventId) &&
                Objects.equals(aggregateId, that.aggregateId) &&
                Objects.equals(aggregateType, that.aggregateType) &&
                Objects.equals(sequenceNumber, that.sequenceNumber) &&
                Objects.equals(eventType, that.eventType) &&
                Objects.equals(eventData, that.eventData) &&
                Objects.equals(metadata, that.metadata) &&
                Objects.equals(timestamp, that.timestamp);
    }

    @Override
    public int hashCode() {
        return Objects.hash(eventId, aggregateId, aggregateType, sequenceNumber, 
                          eventType, eventData, metadata, timestamp);
    }

    @Override
    public String toString() {
        return "KafkaEventMessage{" +
                "eventId='" + eventId + '\'' +
                ", aggregateId='" + aggregateId + '\'' +
                ", aggregateType='" + aggregateType + '\'' +
                ", sequenceNumber=" + sequenceNumber +
                ", eventType='" + eventType + '\'' +
                ", timestamp=" + timestamp +
                '}';
    }
}