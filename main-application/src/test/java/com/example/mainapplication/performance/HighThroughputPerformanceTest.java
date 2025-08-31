package com.example.mainapplication.performance;

import com.example.mainapplication.command.CreateUserCommand;
import com.example.mainapplication.command.UpdateUserCommand;
import com.example.mainapplication.repository.UserProjectionRepository;
import org.axonframework.commandhandling.gateway.CommandGateway;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
public class HighThroughputPerformanceTest {

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
    private UserProjectionRepository userProjectionRepository;

    @BeforeEach
    void setUp() {
        userProjectionRepository.deleteAll();
    }

    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void shouldHandleHighVolumeCommandProcessing() throws Exception {
        // Given
        int numberOfCommands = 1000;
        int threadPoolSize = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadPoolSize);
        List<CompletableFuture<String>> futures = new ArrayList<>();
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);

        Instant startTime = Instant.now();

        // When - Submit commands concurrently
        for (int i = 0; i < numberOfCommands; i++) {
            final int index = i;
            CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
                try {
                    String userId = UUID.randomUUID().toString();
                    CreateUserCommand command = new CreateUserCommand(
                            userId, 
                            "User " + index, 
                            "user" + index + "@example.com"
                    );
                    String result = commandGateway.send(command).get(10, TimeUnit.SECONDS);
                    successCount.incrementAndGet();
                    return result;
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                    throw new RuntimeException(e);
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
        System.out.println("Commands processed: " + successCount.get());
        System.out.println("Errors: " + errorCount.get());
        System.out.println("Duration: " + duration.toMillis() + "ms");
        System.out.println("Commands per second: " + commandsPerSecond);

        assertThat(successCount.get()).isGreaterThan(numberOfCommands * 0.95); // 95% success rate
        assertThat(commandsPerSecond).isGreaterThan(10); // At least 10 commands per second

        executor.shutdown();
    }

    @Test
    @Timeout(value = 90, unit = TimeUnit.SECONDS)
    void shouldHandleMixedWorkloadPerformance() throws Exception {
        // Given
        int numberOfUsers = 500;
        int updatesPerUser = 3;
        ExecutorService executor = Executors.newFixedThreadPool(20);
        List<String> userIds = new ArrayList<>();
        AtomicInteger totalOperations = new AtomicInteger(0);

        Instant startTime = Instant.now();

        // Phase 1: Create users
        List<CompletableFuture<String>> createFutures = new ArrayList<>();
        for (int i = 0; i < numberOfUsers; i++) {
            final int index = i;
            CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
                try {
                    String userId = UUID.randomUUID().toString();
                    CreateUserCommand command = new CreateUserCommand(
                            userId, 
                            "User " + index, 
                            "user" + index + "@example.com"
                    );
                    String result = commandGateway.send(command).get(15, TimeUnit.SECONDS);
                    totalOperations.incrementAndGet();
                    return result;
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }, executor);
            createFutures.add(future);
        }

        // Wait for all creates to complete and collect user IDs
        for (CompletableFuture<String> future : createFutures) {
            userIds.add(future.get());
        }

        // Phase 2: Update users concurrently
        List<CompletableFuture<Void>> updateFutures = new ArrayList<>();
        for (String userId : userIds) {
            for (int update = 0; update < updatesPerUser; update++) {
                final int updateIndex = update;
                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                    try {
                        UpdateUserCommand command = new UpdateUserCommand(
                                userId, 
                                "Updated User " + updateIndex, 
                                "updated" + updateIndex + "@example.com"
                        );
                        commandGateway.send(command).get(15, TimeUnit.SECONDS);
                        totalOperations.incrementAndGet();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }, executor);
                updateFutures.add(future);
            }
        }

        // Wait for all updates to complete
        CompletableFuture.allOf(updateFutures.toArray(new CompletableFuture[0])).get(60, TimeUnit.SECONDS);

        Instant endTime = Instant.now();
        Duration duration = Duration.between(startTime, endTime);

        // Then - Verify performance metrics
        int expectedOperations = numberOfUsers + (numberOfUsers * updatesPerUser);
        double operationsPerSecond = totalOperations.get() / (duration.toMillis() / 1000.0);
        
        System.out.println("Total operations: " + totalOperations.get());
        System.out.println("Expected operations: " + expectedOperations);
        System.out.println("Duration: " + duration.toMillis() + "ms");
        System.out.println("Operations per second: " + operationsPerSecond);

        assertThat(totalOperations.get()).isEqualTo(expectedOperations);
        assertThat(operationsPerSecond).isGreaterThan(5); // At least 5 operations per second

        executor.shutdown();
    }

    @Test
    @Timeout(value = 120, unit = TimeUnit.SECONDS)
    void shouldMaintainPerformanceUnderSustainedLoad() throws Exception {
        // Given
        int durationSeconds = 60;
        int targetThroughput = 20; // commands per second
        ExecutorService executor = Executors.newFixedThreadPool(15);
        AtomicInteger commandCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);
        volatile boolean shouldStop = false;

        Instant startTime = Instant.now();

        // When - Generate sustained load
        List<CompletableFuture<Void>> workers = new ArrayList<>();
        for (int worker = 0; worker < 5; worker++) {
            CompletableFuture<Void> workerFuture = CompletableFuture.runAsync(() -> {
                while (!shouldStop) {
                    try {
                        String userId = UUID.randomUUID().toString();
                        CreateUserCommand command = new CreateUserCommand(
                                userId, 
                                "Load User " + commandCount.get(), 
                                "load" + commandCount.get() + "@example.com"
                        );
                        commandGateway.send(command).get(5, TimeUnit.SECONDS);
                        commandCount.incrementAndGet();
                        
                        // Control throughput
                        Thread.sleep(1000 / targetThroughput);
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
        double actualThroughput = commandCount.get() / (actualDuration.toMillis() / 1000.0);
        double errorRate = (double) errorCount.get() / commandCount.get();

        System.out.println("Commands processed: " + commandCount.get());
        System.out.println("Errors: " + errorCount.get());
        System.out.println("Actual duration: " + actualDuration.toSeconds() + "s");
        System.out.println("Actual throughput: " + actualThroughput + " commands/sec");
        System.out.println("Error rate: " + (errorRate * 100) + "%");

        assertThat(actualThroughput).isGreaterThan(targetThroughput * 0.8); // Within 80% of target
        assertThat(errorRate).isLessThan(0.05); // Less than 5% error rate

        executor.shutdown();
    }
}