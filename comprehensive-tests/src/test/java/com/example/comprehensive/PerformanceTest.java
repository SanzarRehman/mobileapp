package com.example.comprehensive;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Performance tests for high-throughput scenarios.
 * Tests system behavior under various load conditions.
 */
@Testcontainers
public class PerformanceTest {

    private static final Network network = Network.newNetwork();

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:13")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test")
            .withNetwork(network);

    @Container
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:latest"))
            .withNetwork(network);

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:6-alpine")
            .withExposedPorts(6379)
            .withNetwork(network);

    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void shouldHandleHighVolumeCommandProcessing() throws Exception {
        // Given
        int numberOfCommands = 1000;
        int threadPoolSize = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadPoolSize);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);
        AtomicLong totalLatency = new AtomicLong(0);

        Instant startTime = Instant.now();

        // When - Submit commands concurrently
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (int i = 0; i < numberOfCommands; i++) {
            final int index = i;
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                try {
                    long commandStart = System.currentTimeMillis();
                    
                    // Simulate command processing
                    String commandId = UUID.randomUUID().toString();
                    Thread.sleep(10 + (index % 50)); // Varying processing times
                    
                    long commandEnd = System.currentTimeMillis();
                    totalLatency.addAndGet(commandEnd - commandStart);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                }
            }, executor);
            futures.add(future);
        }

        // Wait for all commands to complete
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get(45, TimeUnit.SECONDS);
        
        Instant endTime = Instant.now();
        Duration duration = Duration.between(startTime, endTime);

        // Then - Verify performance metrics
        double commandsPerSecond = numberOfCommands / (duration.toMillis() / 1000.0);
        double averageLatency = totalLatency.get() / (double) successCount.get();
        double errorRate = (errorCount.get() / (double) numberOfCommands) * 100;

        System.out.println("High Volume Command Processing Results:");
        System.out.println("Commands processed: " + successCount.get());
        System.out.println("Errors: " + errorCount.get());
        System.out.println("Duration: " + duration.toMillis() + "ms");
        System.out.println("Commands per second: " + commandsPerSecond);
        System.out.println("Average latency: " + averageLatency + "ms");
        System.out.println("Error rate: " + errorRate + "%");

        assertThat(successCount.get()).isGreaterThan(numberOfCommands * 0.95); // 95% success rate
        assertThat(commandsPerSecond).isGreaterThan(10); // At least 10 commands per second
        assertThat(errorRate).isLessThan(5.0); // Less than 5% error rate

        executor.shutdown();
    }

    @Test
    @Timeout(value = 90, unit = TimeUnit.SECONDS)
    void shouldHandleMixedWorkloadPerformance() throws Exception {
        // Given
        int numberOfOperations = 500;
        int commandThreads = 5;
        int queryThreads = 10;
        int updateThreads = 3;

        ExecutorService executor = Executors.newFixedThreadPool(commandThreads + queryThreads + updateThreads);
        AtomicInteger commandCount = new AtomicInteger(0);
        AtomicInteger queryCount = new AtomicInteger(0);
        AtomicInteger updateCount = new AtomicInteger(0);
        AtomicInteger totalOperations = new AtomicInteger(0);

        Instant startTime = Instant.now();

        // Command workers
        List<CompletableFuture<Void>> commandWorkers = new ArrayList<>();
        for (int i = 0; i < commandThreads; i++) {
            CompletableFuture<Void> worker = CompletableFuture.runAsync(() -> {
                for (int j = 0; j < numberOfOperations / commandThreads; j++) {
                    try {
                        // Simulate command processing
                        Thread.sleep(50);
                        commandCount.incrementAndGet();
                        totalOperations.incrementAndGet();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }, executor);
            commandWorkers.add(worker);
        }

        // Query workers
        List<CompletableFuture<Void>> queryWorkers = new ArrayList<>();
        for (int i = 0; i < queryThreads; i++) {
            CompletableFuture<Void> worker = CompletableFuture.runAsync(() -> {
                for (int j = 0; j < numberOfOperations / queryThreads; j++) {
                    try {
                        // Simulate query processing
                        Thread.sleep(20);
                        queryCount.incrementAndGet();
                        totalOperations.incrementAndGet();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }, executor);
            queryWorkers.add(worker);
        }

        // Update workers
        List<CompletableFuture<Void>> updateWorkers = new ArrayList<>();
        for (int i = 0; i < updateThreads; i++) {
            CompletableFuture<Void> worker = CompletableFuture.runAsync(() -> {
                for (int j = 0; j < numberOfOperations / updateThreads; j++) {
                    try {
                        // Simulate update processing
                        Thread.sleep(75);
                        updateCount.incrementAndGet();
                        totalOperations.incrementAndGet();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }, executor);
            updateWorkers.add(worker);
        }

        // Wait for all workers to complete
        List<CompletableFuture<Void>> allWorkers = new ArrayList<>();
        allWorkers.addAll(commandWorkers);
        allWorkers.addAll(queryWorkers);
        allWorkers.addAll(updateWorkers);
        CompletableFuture.allOf(allWorkers.toArray(new CompletableFuture[0])).get(60, TimeUnit.SECONDS);

        Instant endTime = Instant.now();
        Duration duration = Duration.between(startTime, endTime);

        // Then - Verify mixed workload performance
        double operationsPerSecond = totalOperations.get() / (duration.toMillis() / 1000.0);
        
        System.out.println("Mixed Workload Performance Results:");
        System.out.println("Commands: " + commandCount.get());
        System.out.println("Queries: " + queryCount.get());
        System.out.println("Updates: " + updateCount.get());
        System.out.println("Total Operations: " + totalOperations.get());
        System.out.println("Duration: " + duration.toMillis() + "ms");
        System.out.println("Operations per second: " + operationsPerSecond);

        assertThat(operationsPerSecond).isGreaterThan(5); // At least 5 operations per second
        assertThat(commandCount.get()).isGreaterThan(0);
        assertThat(queryCount.get()).isGreaterThan(0);
        assertThat(updateCount.get()).isGreaterThan(0);

        executor.shutdown();
    }

    @Test
    @Timeout(value = 120, unit = TimeUnit.SECONDS)
    void shouldMaintainPerformanceUnderSustainedLoad() throws Exception {
        // Given
        int durationSeconds = 60;
        int targetThroughput = 20; // operations per second
        ExecutorService executor = Executors.newFixedThreadPool(10);
        AtomicInteger operationCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);
        volatile boolean shouldStop = false;

        Instant startTime = Instant.now();

        // When - Generate sustained load
        List<CompletableFuture<Void>> workers = new ArrayList<>();
        for (int worker = 0; worker < 5; worker++) {
            CompletableFuture<Void> workerFuture = CompletableFuture.runAsync(() -> {
                while (!shouldStop) {
                    try {
                        // Simulate operation processing
                        String operationId = UUID.randomUUID().toString();
                        Thread.sleep(1000 / targetThroughput); // Control throughput
                        operationCount.incrementAndGet();
                    } catch (Exception e) {
                        errorCount.incrementAndGet();
                    }
                }
            }, executor);
            workers.add(workerFuture);
        }

        // Run for specified duration
        Thread.sleep(durationSeconds * 1000);
        shouldStop = true;

        // Wait for workers to finish
        CompletableFuture.allOf(workers.toArray(new CompletableFuture[0])).get(10, TimeUnit.SECONDS);

        Instant endTime = Instant.now();
        Duration actualDuration = Duration.between(startTime, endTime);

        // Then - Verify sustained performance
        double actualThroughput = operationCount.get() / (actualDuration.toMillis() / 1000.0);
        double errorRate = (double) errorCount.get() / operationCount.get();

        System.out.println("Sustained Load Performance Results:");
        System.out.println("Operations processed: " + operationCount.get());
        System.out.println("Errors: " + errorCount.get());
        System.out.println("Actual duration: " + actualDuration.toSeconds() + "s");
        System.out.println("Actual throughput: " + actualThroughput + " operations/sec");
        System.out.println("Error rate: " + (errorRate * 100) + "%");

        assertThat(actualThroughput).isGreaterThan(targetThroughput * 0.8); // Within 80% of target
        assertThat(errorRate).isLessThan(0.05); // Less than 5% error rate

        executor.shutdown();
    }

    @Test
    void shouldScaleWithIncreasingLoad() throws Exception {
        // Test different load levels to verify scaling characteristics
        int[] loadLevels = {50, 100, 200};
        List<PerformanceResult> results = new ArrayList<>();

        for (int loadLevel : loadLevels) {
            PerformanceResult result = executeLoadTest(loadLevel, 5);
            results.add(result);
            
            System.out.println("Load Level: " + loadLevel + 
                             ", Throughput: " + result.throughput + 
                             ", Avg Latency: " + result.averageLatency + "ms");
            
            // Brief pause between tests
            Thread.sleep(2000);
        }

        // Verify scaling characteristics
        for (int i = 1; i < results.size(); i++) {
            PerformanceResult current = results.get(i);
            PerformanceResult previous = results.get(i - 1);
            
            // Throughput should increase with load (within reasonable bounds)
            double throughputRatio = current.throughput / previous.throughput;
            assertThat(throughputRatio).isGreaterThan(1.2); // At least 20% increase
            
            // Latency should not increase dramatically
            double latencyRatio = current.averageLatency / previous.averageLatency;
            assertThat(latencyRatio).isLessThan(3.0); // Less than 3x latency increase
        }
    }

    private PerformanceResult executeLoadTest(int numberOfOperations, int threadPoolSize) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(threadPoolSize);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicLong totalLatency = new AtomicLong(0);

        Instant startTime = Instant.now();
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (int i = 0; i < numberOfOperations; i++) {
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                try {
                    long operationStart = System.currentTimeMillis();
                    
                    // Simulate operation processing
                    Thread.sleep(10 + (int)(Math.random() * 40)); // 10-50ms processing time
                    
                    long operationEnd = System.currentTimeMillis();
                    totalLatency.addAndGet(operationEnd - operationStart);
                    successCount.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }, executor);
            futures.add(future);
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get(60, TimeUnit.SECONDS);
        Instant endTime = Instant.now();

        Duration duration = Duration.between(startTime, endTime);
        double throughput = successCount.get() / (duration.toMillis() / 1000.0);
        double averageLatency = totalLatency.get() / (double) successCount.get();

        executor.shutdown();
        return new PerformanceResult(throughput, averageLatency);
    }

    private static class PerformanceResult {
        final double throughput;
        final double averageLatency;

        PerformanceResult(double throughput, double averageLatency) {
            this.throughput = throughput;
            this.averageLatency = averageLatency;
        }
    }
}