package com.example.customaxonserver.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for PartitioningStrategy.
 * Tests the consistent partitioning logic for Kafka message distribution.
 */
class PartitioningStrategyTest {

    private PartitioningStrategy partitioningStrategy;

    @BeforeEach
    void setUp() {
        partitioningStrategy = new PartitioningStrategy();
    }

    @Test
    void shouldReturnConsistentPartitionForSameAggregateId() {
        // Given
        String aggregateId = "test-aggregate-123";
        int totalPartitions = 3;

        // When
        int partition1 = partitioningStrategy.getPartitionForAggregate(aggregateId, totalPartitions);
        int partition2 = partitioningStrategy.getPartitionForAggregate(aggregateId, totalPartitions);

        // Then
        assertThat(partition1).isEqualTo(partition2);
        assertThat(partition1).isBetween(0, totalPartitions - 1);
    }

    @Test
    void shouldDistributeAggregatesAcrossPartitions() {
        // Given
        int totalPartitions = 3;
        String[] aggregateIds = {
            "aggregate-1", "aggregate-2", "aggregate-3", "aggregate-4", 
            "aggregate-5", "aggregate-6", "aggregate-7", "aggregate-8", "aggregate-9"
        };

        // When
        Map<Integer, Integer> partitionCounts = new HashMap<>();
        for (String aggregateId : aggregateIds) {
            int partition = partitioningStrategy.getPartitionForAggregate(aggregateId, totalPartitions);
            partitionCounts.merge(partition, 1, Integer::sum);
        }

        // Then
        assertThat(partitionCounts.keySet()).hasSize(totalPartitions);
        for (int i = 0; i < totalPartitions; i++) {
            assertThat(partitionCounts).containsKey(i);
            assertThat(partitionCounts.get(i)).isGreaterThan(0);
        }
    }

    @Test
    void shouldHandleNullAggregateId() {
        // Given
        String aggregateId = null;
        int totalPartitions = 3;

        // When
        int partition = partitioningStrategy.getPartitionForAggregate(aggregateId, totalPartitions);

        // Then
        assertThat(partition).isEqualTo(0);
    }

    @Test
    void shouldHandleEmptyAggregateId() {
        // Given
        String aggregateId = "";
        int totalPartitions = 3;

        // When
        int partition = partitioningStrategy.getPartitionForAggregate(aggregateId, totalPartitions);

        // Then
        assertThat(partition).isEqualTo(0);
    }

    @Test
    void shouldHandleSinglePartition() {
        // Given
        String aggregateId = "test-aggregate-123";
        int totalPartitions = 1;

        // When
        int partition = partitioningStrategy.getPartitionForAggregate(aggregateId, totalPartitions);

        // Then
        assertThat(partition).isEqualTo(0);
    }

    @Test
    void shouldReturnCorrectPartitionKey() {
        // Given
        String aggregateId = "test-aggregate-123";

        // When
        String partitionKey = partitioningStrategy.getPartitionKey(aggregateId);

        // Then
        assertThat(partitionKey).isEqualTo(aggregateId);
    }

    @Test
    void shouldReturnDefaultPartitionKeyForNullAggregateId() {
        // Given
        String aggregateId = null;

        // When
        String partitionKey = partitioningStrategy.getPartitionKey(aggregateId);

        // Then
        assertThat(partitionKey).isEqualTo("default");
    }

    @Test
    void shouldValidateAggregateId() {
        // Test valid aggregate IDs
        assertThat(partitioningStrategy.isValidAggregateId("valid-aggregate-123")).isTrue();
        assertThat(partitioningStrategy.isValidAggregateId("a")).isTrue();
        assertThat(partitioningStrategy.isValidAggregateId("  valid  ")).isTrue();

        // Test invalid aggregate IDs
        assertThat(partitioningStrategy.isValidAggregateId(null)).isFalse();
        assertThat(partitioningStrategy.isValidAggregateId("")).isFalse();
        assertThat(partitioningStrategy.isValidAggregateId("   ")).isFalse();
    }

    @Test
    void shouldProduceEvenDistributionWithManyAggregates() {
        // Given
        int totalPartitions = 5;
        int numberOfAggregates = 1000;
        
        // When
        Map<Integer, Integer> partitionCounts = new HashMap<>();
        for (int i = 0; i < numberOfAggregates; i++) {
            String aggregateId = "aggregate-" + i;
            int partition = partitioningStrategy.getPartitionForAggregate(aggregateId, totalPartitions);
            partitionCounts.merge(partition, 1, Integer::sum);
        }

        // Then
        assertThat(partitionCounts.keySet()).hasSize(totalPartitions);
        
        // Check that distribution is reasonably even (within 20% of expected)
        int expectedPerPartition = numberOfAggregates / totalPartitions;
        int tolerance = (int) (expectedPerPartition * 0.2);
        
        for (int count : partitionCounts.values()) {
            assertThat(count).isBetween(expectedPerPartition - tolerance, expectedPerPartition + tolerance);
        }
    }

    @Test
    void shouldHandleLargeNumberOfPartitions() {
        // Given
        String aggregateId = "test-aggregate-123";
        int totalPartitions = 1000;

        // When
        int partition = partitioningStrategy.getPartitionForAggregate(aggregateId, totalPartitions);

        // Then
        assertThat(partition).isBetween(0, totalPartitions - 1);
    }
}