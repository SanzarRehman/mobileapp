package com.example.customaxonserver.util;

import org.springframework.stereotype.Component;

/**
 * Utility class for determining Kafka partition assignments.
 * Implements consistent partitioning based on aggregate ID to ensure
 * events for the same aggregate are processed in order.
 */
@Component
public class PartitioningStrategy {

    /**
     * Determines the partition for an aggregate based on its ID.
     * Uses consistent hashing to ensure the same aggregate always
     * goes to the same partition, maintaining event ordering.
     *
     * @param aggregateId the aggregate identifier
     * @param totalPartitions the total number of partitions available
     * @return the partition number (0-based)
     */
    public int getPartitionForAggregate(String aggregateId, int totalPartitions) {
        if (aggregateId == null || aggregateId.isEmpty()) {
            return 0;
        }
        
        // Use consistent hashing to distribute aggregates across partitions
        // This ensures the same aggregate always goes to the same partition
        return Math.abs(aggregateId.hashCode()) % totalPartitions;
    }

    /**
     * Determines the partition key for Kafka message routing.
     * The partition key is used by Kafka's default partitioner to
     * determine which partition to send the message to.
     *
     * @param aggregateId the aggregate identifier
     * @return the partition key
     */
    public String getPartitionKey(String aggregateId) {
        return aggregateId != null ? aggregateId : "default";
    }

    /**
     * Validates that the aggregate ID is suitable for partitioning.
     *
     * @param aggregateId the aggregate identifier to validate
     * @return true if the aggregate ID is valid for partitioning
     */
    public boolean isValidAggregateId(String aggregateId) {
        return aggregateId != null && !aggregateId.trim().isEmpty();
    }
}