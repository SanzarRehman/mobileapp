package com.example.mainapplication.load;

import com.example.mainapplication.command.CreateUserCommand;
import com.example.mainapplication.command.UpdateUserCommand;
import com.example.mainapplication.query.FindUserByIdQuery;
import com.example.mainapplication.repository.UserProjectionRepository;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.queryhandling.QueryGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
public class ScalabilityLoadTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:13")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

    @Container
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:latest"));

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:6-alpine")
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("spring.redis.host", redis::getHost);
        registry.add("spring.redis.port", () -> redis.getMappedPort(6379));
        registry.add("custom.axon.server.url", () -> "http://localhost:8081");
    }

    @Autowired
    private CommandGateway commandGateway;

    @Autowired
    private QueryGateway queryGateway;

    @Autowired
    private UserProjectionRepository userProjectionRepository;

    @BeforeEach
    void setUp() {
        userProjectionRepository.deleteAll();
    }

    @Test
    @Timeout(value = 300, unit = TimeUnit.SECONDS)
    void shouldScaleCommandProcessingLinearly() throws Exception {
        // Test different load levels to verify linear scaling
        int[] loadLevels = {100, 500, 1000};
        List<LoadTestResult> results = new ArrayList<>();

        for (int loadLevel : loadLevels) {
            LoadTestResult result = executeCommandLoadTest(loadLevel, 20);
            results.add(result);
            
            System.out.println("Load Level: " + loadLevel + 
                             ", Throughput: " + result.throughput + 
                             ", Avg Latency: " + result.averageLatency + "ms" +
                             ", Error Rate: " + result.errorRate + "%");
            
            // Clean up between tests
            Thread.sleep(5000);
            userProjectionRepository.deleteAll();
        }

        // Verify scaling characteristics
        for (int i = 1; i < results.size(); i++) {
            LoadTestResult current = results.get(i);
            LoadTestResult previous = results.get(i - 1);
            
            // Throughput should increase with load (within reasonable bounds)
            double throughputRatio = current.throughput / previous.throughput;
            assertThat(throughputRatio).isGreaterThan(1.5); // At least 50% increase
            
            // Error rate should remain low
            assertThat(current.errorRate).isLessThan(5.0); // Less than 5% errors
            
            // Latency should not increase dramatically
            double latencyRatio = current.averageLatency / previous.averageLatency;
            assertThat(latencyRatio).isLessThan(3.0); // Less than 3x latency increase
        }
    }

    @Test
    @Timeout(value = 180, unit = TimeUnit.SECONDS)
    void shouldHandleQueryLoadEfficiently() throws Exception {
        // Given - Create test data first
        int numberOfUsers = 1000;
        List<String> userIds = new ArrayList<>();
        
        for (int i = 0; i < numberOfUsers; i++) {
            String userId = UUID.randomUUID().toString();
            CreateUserCommand command = new CreateUserCommand(userId, "User " + i, "user" + i + "@example.com");
            commandGateway.send(command).get(10, TimeUnit.SECONDS);
            userIds.add(userId);
        }

        // Wait for projections to be built
        Thread.sleep(10000);

        // When - Execute query load test
        int queryLoad = 2000;
        int threadPoolSize = 50;
        ExecutorService executor = Executors.newFixedThreadPool(threadPoolSize);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);
        AtomicLong totalLatency = new AtomicLong(0);

        Instant startTime = Instant.now();
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (int i = 0; i < queryLoad; i++) {
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                try {
                    long queryStart = System.currentTimeMillis();
                    String randomUserId = userIds.get(ThreadLocalRandom.current().nextInt(userIds.size()));
                    
                    queryGateway.query(new FindUserByIdQuery(randomUserId), Object.class)
                              .get(5, TimeUnit.SECONDS);
                    
                    long queryEnd = System.currentTimeMillis();
                    totalLatency.addAndGet(queryEnd - queryStart);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                }
            }, executor);
            futures.add(future);
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get(120, TimeUnit.SECONDS);
        Instant endTime = Instant.now();

        // Then - Verify query performance
        Duration duration = Duration.between(startTime, endTime);
        double queryThroughput = successCount.get() / (duration.toMillis() / 1000.0);
        double averageLatency = totalLatency.get() / (double) successCount.get();
        double errorRate = (errorCount.get() / (double) queryLoad) * 100;

        System.out.println("Query Load Test Results:");
        System.out.println("Queries: " + queryLoad);
        System.out.println("Successful: " + successCount.get());
        System.out.println("Errors: " + errorCount.get());
        System.out.println("Throughput: " + queryThroughput + " queries/sec");
        System.out.println("Average Latency: " + averageLatency + "ms");
        System.out.println("Error Rate: " + errorRate + "%");

        assertThat(queryThroughput).isGreaterThan(50); // At least 50 queries per second
        assertThat(averageLatency).isLessThan(1000); // Less than 1 second average
        assertThat(errorRate).isLessThan(2.0); // Less than 2% error rate

        executor.shutdown();
    }

    @Test
    @Timeout(value = 240, unit = TimeUnit.SECONDS)
    void shouldMaintainPerformanceUnderMixedLoad() throws Exception {
        // Test mixed workload: commands, queries, and updates
        int testDurationSeconds = 120;
        int commandThreads = 10;
        int queryThreads = 20;
        int updateThreads = 5;

        ExecutorService executor = Executors.newFixedThreadPool(commandThreads + queryThreads + updateThreads);
        AtomicInteger commandCount = new AtomicInteger(0);
        AtomicInteger queryCount = new AtomicInteger(0);
        AtomicInteger updateCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);
        List<String> createdUserIds = new CopyOnWriteArrayList<>();
        volatile boolean shouldStop = false;

        Instant startTime = Instant.now();

        // Command workers
        List<CompletableFuture<Void>> commandWorkers = new ArrayList<>();
        for (int i = 0; i < commandThreads; i++) {
            CompletableFuture<Void> worker = CompletableFuture.runAsync(() -> {
                while (!shouldStop) {
                    try {
                        String userId = UUID.randomUUID().toString();
                        CreateUserCommand command = new CreateUserCommand(userId, "Mixed User", "mixed@example.com");
                        commandGateway.send(command).get(5, TimeUnit.SECONDS);
                        createdUserIds.add(userId);
                        commandCount.incrementAndGet();
                        Thread.sleep(100); // Control rate
                    } catch (Exception e) {
                        errorCount.incrementAndGet();
                    }
                }
            }, executor);
            commandWorkers.add(worker);
        }

        // Query workers
        List<CompletableFuture<Void>> queryWorkers = new ArrayList<>();
        for (int i = 0; i < queryThreads; i++) {
            CompletableFuture<Void> worker = CompletableFuture.runAsync(() -> {
                while (!shouldStop) {
                    try {
                        if (!createdUserIds.isEmpty()) {
                            String randomUserId = createdUserIds.get(
                                ThreadLocalRandom.current().nextInt(createdUserIds.size()));
                            queryGateway.query(new FindUserByIdQuery(randomUserId), Object.class)
                                      .get(3, TimeUnit.SECONDS);
                            queryCount.incrementAndGet();
                        }
                        Thread.sleep(50); // Higher query rate
                    } catch (Exception e) {
                        errorCount.incrementAndGet();
                    }
                }
            }, executor);
            queryWorkers.add(worker);
        }

        // Update workers
        List<CompletableFuture<Void>> updateWorkers = new ArrayList<>();
        for (int i = 0; i < updateThreads; i++) {
            CompletableFuture<Void> worker = CompletableFuture.runAsync(() -> {
                while (!shouldStop) {
                    try {
                        if (!createdUserIds.isEmpty()) {
                            String randomUserId = createdUserIds.get(
                                ThreadLocalRandom.current().nextInt(createdUserIds.size()));
                            UpdateUserCommand command = new UpdateUserCommand(randomUserId, "Updated User", "updated@example.com");
                            commandGateway.send(command).get(5, TimeUnit.SECONDS);
                            updateCount.incrementAndGet();
                        }
                        Thread.sleep(200); // Lower update rate
                    } catch (Exception e) {
                        errorCount.incrementAndGet();
                    }
                }
            }, executor);
            updateWorkers.add(worker);
        }

        // Run for specified duration
        Thread.sleep(testDurationSeconds * 1000);
        shouldStop = true;

        // Wait for all workers to finish
        List<CompletableFuture<Void>> allWorkers = new ArrayList<>();
        allWorkers.addAll(commandWorkers);
        allWorkers.addAll(queryWorkers);
        allWorkers.addAll(updateWorkers);
        CompletableFuture.allOf(allWorkers.toArray(new CompletableFuture[0])).get(30, TimeUnit.SECONDS);

        Instant endTime = Instant.now();
        Duration actualDuration = Duration.between(startTime, endTime);

        // Calculate metrics
        int totalOperations = commandCount.get() + queryCount.get() + updateCount.get();
        double operationsPerSecond = totalOperations / (actualDuration.toMillis() / 1000.0);
        double errorRate = (errorCount.get() / (double) totalOperations) * 100;

        System.out.println("Mixed Load Test Results:");
        System.out.println("Commands: " + commandCount.get());
        System.out.println("Queries: " + queryCount.get());
        System.out.println("Updates: " + updateCount.get());
        System.out.println("Total Operations: " + totalOperations);
        System.out.println("Errors: " + errorCount.get());
        System.out.println("Duration: " + actualDuration.toSeconds() + "s");
        System.out.println("Operations/sec: " + operationsPerSecond);
        System.out.println("Error Rate: " + errorRate + "%");

        // Verify performance under mixed load
        assertThat(operationsPerSecond).isGreaterThan(20); // At least 20 operations per second
        assertThat(errorRate).isLessThan(5.0); // Less than 5% error rate
        assertThat(commandCount.get()).isGreaterThan(0);
        assertThat(queryCount.get()).isGreaterThan(0);
        assertThat(updateCount.get()).isGreaterThan(0);

        executor.shutdown();
    }

    private LoadTestResult executeCommandLoadTest(int numberOfCommands, int threadPoolSize) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(threadPoolSize);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);
        AtomicLong totalLatency = new AtomicLong(0);

        Instant startTime = Instant.now();
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (int i = 0; i < numberOfCommands; i++) {
            final int index = i;
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                try {
                    long commandStart = System.currentTimeMillis();
                    String userId = UUID.randomUUID().toString();
                    CreateUserCommand command = new CreateUserCommand(userId, "Load User " + index, "load" + index + "@example.com");
                    commandGateway.send(command).get(15, TimeUnit.SECONDS);
                    long commandEnd = System.currentTimeMillis();
                    
                    totalLatency.addAndGet(commandEnd - commandStart);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                }
            }, executor);
            futures.add(future);
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get(180, TimeUnit.SECONDS);
        Instant endTime = Instant.now();

        Duration duration = Duration.between(startTime, endTime);
        double throughput = successCount.get() / (duration.toMillis() / 1000.0);
        double averageLatency = totalLatency.get() / (double) successCount.get();
        double errorRate = (errorCount.get() / (double) numberOfCommands) * 100;

        executor.shutdown();
        return new LoadTestResult(throughput, averageLatency, errorRate);
    }

    private static class LoadTestResult {
        final double throughput;
        final double averageLatency;
        final double errorRate;

        LoadTestResult(double throughput, double averageLatency, double errorRate) {
            this.throughput = throughput;
            this.averageLatency = averageLatency;
            this.errorRate = errorRate;
        }
    }
}